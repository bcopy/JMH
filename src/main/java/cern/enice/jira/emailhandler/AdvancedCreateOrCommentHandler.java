package cern.enice.jira.emailhandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;

import com.atlassian.jira.user.UserUtils;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.bc.issue.IssueService.UpdateValidationResult;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.IssueInputParametersImpl;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.IssueUtilsBean;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.service.util.ServiceUtils;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.jira.plugins.mail.handlers.AbstractMessageHandler;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.JiraUtils;
import com.atlassian.jira.workflow.IssueWorkflowManagerImpl;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowTransitionUtil;
import com.atlassian.jira.workflow.WorkflowTransitionUtilImpl;
import com.atlassian.mail.MailUtils;
import com.atlassian.crowd.embedded.api.User;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.atlassian.jira.workflow.IssueWorkflowManager;


/**
 * This is the main handler class of the JIRA Advanced Mail Handler.
 * <p>
 * It permits to create a new issue, or add a comment to an existing issue, from
 * an incoming message. If the recipient or subject contains a project key the
 * message is added as a comment to that issue; in this case, many of the issue
 * options can be specified directly in the email body. If no project key is
 * found, a new issue is created in the specified project.
 * 
 * @author Brice Copy on the basis of DAniele Raffo's work and the
 *         CreateOrCommentHandler class, copyright (c) 2002-2006 Atlassian
 */
public class AdvancedCreateOrCommentHandler extends AbstractMessageHandler {

	public class RegexpWhitelistMatchPredicate implements Predicate {

		String m_fromAddress;

		public RegexpWhitelistMatchPredicate(String fromAddress) {
			m_fromAddress = fromAddress;
		}

		public boolean evaluate(Object object) {
			String whitelistExpression = (String) object;

			log.debug("Matching " + m_fromAddress + " with "
					+ whitelistExpression);
			if (Pattern.matches(whitelistExpression, m_fromAddress)) {
				return true;
			}
			return false;
		}

	}

	private final Logger log = Logger
			.getLogger(AdvancedCreateOrCommentHandler.class);

	/** Default project where new issues are created. */
	public String defaultProjectKey;
	/** Default type for new issues. */
	public String defaultIssueType;
	/** If set (to anything), quoted text is removed from comments. */
	public String stripquotes;
	/** Regex for JIRA email address. */
	public String jiraEmail;
	/** Regex for alias of JIRA email address. */
	public String jiraEmailAlias;
	/** Reporter's username */
	public String reporterUsername;

	/**
	 * Collection of patterns defining messages scraped from subject to append
	 * to issue summary
	 */
	public Map<String, SubjectRegexpReplace> subjectregexps = new HashMap<String, SubjectRegexpReplace>();

	public List<String> whiteListEntries = new ArrayList<String>();

	private static final String KEY_PROJECT = "project";
	private static final String KEY_ISSUETYPE = "issuetype";
	private static final String KEY_QUOTES = "stripquotes";
	private static final String FALSE = "false";
	private static final String KEY_JIRAEMAIL = "jiraemail";
	private static final String KEY_JIRAALIAS = "jiraalias";
	private static final String KEY_WHITELIST = "whitelist";
	private static final String KEY_SUBJECTREGEXP = "subjectregexp";
	private static final String KEY_SUBJECTREPLACE = "subjectreplace";
	private static final String KEY_REPORTERUSERNAME = "reporterusername";

	public void init(Map params, MessageHandlerErrorCollector monitor) {
		log.debug("AdvancedCreateOrCommentHandler.init(params: " + params + ")");

		super.init(params, monitor);

		if (params.containsKey(KEY_PROJECT)) {
			defaultProjectKey = (String) params.get(KEY_PROJECT);
		}

		if (params.containsKey(KEY_ISSUETYPE)) {
			defaultIssueType = (String) params.get(KEY_ISSUETYPE);
		}

		if (params.containsKey(KEY_QUOTES)) {
			stripquotes = (String) params.get(KEY_QUOTES);
		}

		if (params.containsKey(KEY_JIRAEMAIL)) {
			jiraEmail = (String) params.get(KEY_JIRAEMAIL);
		}

		if (params.containsKey(KEY_REPORTERUSERNAME)) {
			reporterUsername = (String) params.get(KEY_REPORTERUSERNAME);
		}

		if (params.containsKey(KEY_JIRAALIAS)) {
			jiraEmailAlias = (String) params.get(KEY_JIRAALIAS);
		} else {
			jiraEmailAlias = jiraEmail;
		}

		for (Object key : params.keySet()) {
			if (((String) key).toLowerCase().trim().startsWith(KEY_WHITELIST)) {
				String whitelistExp = (String) params.get(key);
				whiteListEntries.add(whitelistExp.trim());
				log.debug("Adding whitelist expression : '" + whitelistExp
						+ "'");
			}
			if (((String) key).toLowerCase().trim()
					.startsWith(KEY_SUBJECTREGEXP)) {
				String subjectregexp = (String) params.get(key);
				log.debug("Found subject regexp pattern : '" + subjectregexp
						+ "'");
				Pattern pattern = Pattern.compile(KEY_SUBJECTREGEXP + "(.*)");
				Matcher matcher = pattern.matcher(key.toString());
				if (matcher.find()) {
					String keykey = matcher.group(1);
					if (subjectregexps.containsKey(keykey)) {
						subjectregexps.get(keykey).setRegexp(subjectregexp);
					} else {
						subjectregexps.put(keykey, new SubjectRegexpReplace(
								subjectregexp));
					}
				} else {
					log.warn("Malformed key for subject regexp pattern: " + key);
				}
			}
			if (((String) key).toLowerCase().trim()
					.startsWith(KEY_SUBJECTREPLACE)) {
				String subjectregexp = (String) params.get(key);
				log.debug("Found subject regexp replace pattern : '"
						+ subjectregexp + "'");
				Pattern pattern = Pattern.compile(KEY_SUBJECTREPLACE + "(.*)");
				Matcher matcher = pattern.matcher(key.toString());
				if (matcher.find()) {
					String keykey = matcher.group(1);
					if (subjectregexps.containsKey(keykey)) {
						subjectregexps.get(keykey).setReplace(subjectregexp);
					} else {
						SubjectRegexpReplace srr = new SubjectRegexpReplace();
						srr.setReplace(subjectregexp);
						subjectregexps.put(keykey, srr);
					}
				} else {
					log.warn("Malformed key for subject replace pattern: "
							+ key);
				}
			}
		}

		log.debug("Params: " + defaultProjectKey + " - " + defaultIssueType
				+ " - " + stripquotes + " - " + jiraEmail + " - "
				+ jiraEmailAlias);
	}

	@SuppressWarnings("deprecation")
	public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException {
		log.debug("AdvancedCreateOrCommentHandler.handleMessage");

		if (!canHandleMessage(message, context.getMonitor())) {
			return deleteEmail;
		}

		String subject = message.getSubject();
		GenericValue issue = ServiceUtils.findIssueInString(subject);

		IssueDescriptor issueDescriptor = MessageParser.parse(message,
				new String[] { jiraEmail, jiraEmailAlias });

		if (issue == null) {
			// If we cannot find the issue from the subject of the e-mail
			// message
			// try finding the issue using the in-reply-to message id of the
			// e-mail message
			Issue associatedIssue = getAssociatedIssue(message);
			if (associatedIssue != null) {
				issue = associatedIssue.getGenericValue();
			}
		}

		// Store the FROM address for later (if there's no FROM, we discard the
		// message !)
		Address[] from = message.getFrom();
		if (from == null || from.length == 0) {
			// No FROM address !?! Have the message deleted, we simply ignore it
			// !
			log.warn("Message has no FROM address in its header ! ignoring message...");
			return true;
		}

		// Try and resolve the sender of the message as a valid JIRA user...
		// We do not use the "default reporter" for this purpose, we need to
		// identify
		// the actual sender of the message.
		User sender = null;

		String fromEmail = extractEmailAddressOnly(from[0].toString());
		
		sender = UserUtils.getUserByEmail(fromEmail);
		if (sender != null) {
			log.debug("Found user " + sender.getName() + " for email "
					+ fromEmail);
		} else {
			log.info("Could not find a user for email '" + fromEmail + "'");
		}

		// /////////
		// JMH-17 : If any whitelist expressions are defined and the sender is
		// unknown to JIRA,
		// we must check whether the sender email matches any of the white
		// listed domains
		log.debug("Whitelisted domains count: " + whiteListEntries.size());
		log.debug("Sender is null ? " + (sender == null));

		if (sender == null && whiteListEntries.size() > 0) {

			log.debug("Trying to find a match for " + fromEmail);
			/**
			 * If the from address does not match any of the whitelisted
			 * domains...
			 */
			String matchingWhitelist = (String) CollectionUtils.find(
					whiteListEntries, new RegexpWhitelistMatchPredicate(
							fromEmail));
			if (matchingWhitelist == null) {
				// .. then we delete the message and interrupt the processing
				log.warn("Sender "
						+ fromEmail
						+ " did not match any of the whitelist regular expressions");

				// Not in the whitelist : Have the message deleted, we simply
				// ignore it !
				return true;
			} else {
				log.debug("Sender " + fromEmail
						+ " matched whitelist expression " + matchingWhitelist
						+ ", processing message...");
			}
		}
		// JMH-17
		// ////////

		// If we have found an associated issue, we're processing a comment made
		// to it
		if (issue != null) {
			boolean doDelete = false;

			// ...If we could not identify the sender of the message earlier
			// as a valid user, we dump the sender's email address directly in
			// the comment
			// so it does not get lost
			boolean registerSenderInCommentText = (sender == null);

			// append message to issue summary based on defined regex
			appendRegexToSummary(message, issue, sender);

			// add the message as a comment to the issue...
			if ((stripquotes == null) || FALSE.equalsIgnoreCase(stripquotes)) {
				FullCommentHandler fc = new FullCommentHandler();
				//fc.setErrorHandler(this.getErrorHandler());
				fc.init(params, context.getMonitor());
				fc.setRegisterSenderInCommentText(registerSenderInCommentText);
				doDelete = fc.handleMessage(message, context); // get message with quotes
			} else {
				NonQuotedCommentHandler nq = new NonQuotedCommentHandler();
				//nq.setErrorHandler(this.getErrorHandler());
				nq.init(params, context.getMonitor());
				nq.setRegisterSenderInCommentText(registerSenderInCommentText);
				doDelete = nq.handleMessage(message, context); // get message without
				// quotes
			}

			// ///////
			// JMH-14
			// Progress the issue in the workflow if required
			if (issueDescriptor.getWorkflowTarget() != null
					&& (!"".equals(issueDescriptor.getWorkflowTarget()))) {
				try {
					MutableIssue mutableIssue = ComponentManager.getInstance()
							.getIssueManager()
							.getIssueObject(issue.getString("key"));
					applyWorkflowTransition(mutableIssue, sender,
							issueDescriptor.getWorkflowTarget(),
							issueDescriptor.getResolution());
				} catch (Throwable t) {
					log.error("Could not trigger workflow transition '"
							+ issueDescriptor.getWorkflowTarget()
							+ "' on issue " + issue.get("key"));
				}

			}
			// /////////////

			return doDelete;
		} else { // no issue found, so create new issue in default project
			AdvancedCreateIssueHandler createIssueHandler = new AdvancedCreateIssueHandler();
			createIssueHandler.setIssueDescriptor(issueDescriptor);
			//createIssueHandler.setErrorHandler(this.getErrorHandler());
			createIssueHandler.init(params, context.getMonitor());
			return createIssueHandler.handleMessage(message, context);
		}
	}

	/**
	 * Looks in subject to find patterns defined as subjectregexp parameter and
	 * appends them to issue summary according to subjectreplace parameter.
	 * 
	 * @param message
	 *            Message
	 * @param issue
	 *            Commented issue
	 * @param user
	 *            User doing the edit (must have proper permissions)
	 */

	private void appendRegexToSummary(Message message, GenericValue issue,
			User user) {
		String subject;
		IssueService issueService = ComponentManager.getInstance()
				.getIssueService();
		try {
			if (user == null) {
				user = UserUtils.getUser(reporterUsername);
				if (user == null) {
					log.error("Couldn't get JIRA user '" + reporterUsername + "'");
					return;
				}
			}
			subject = message.getSubject();
			for (String key : subjectregexps.keySet()) {
				Matcher matcher = subjectregexps.get(key).getMatcher(subject);
				while (matcher.find()) {
					String toAppend = subjectregexps.get(key)
							.getOutput(matcher);
					IssueService.IssueResult issueResult = issueService.getIssue(
							user, issue.getString("key"));
					MutableIssue mutableIssue = issueResult.getIssue();
					if (mutableIssue.getSummary().contains(toAppend)) {
						log.info("Subject already contains the output regex. Not appending! Subject: |"+subject+"| and toAppend=|"+toAppend+"|");
					} else {
						log.info("Appending: " + toAppend);
						IssueInputParameters issueInputParameters = new IssueInputParametersImpl();
						issueInputParameters.setSummary(mutableIssue
								.getSummary() + " " + toAppend);
						UpdateValidationResult updateValidationResult = issueService
								.validateUpdate(user, mutableIssue.getId(),
										issueInputParameters);
						if (updateValidationResult.isValid()) {
							IssueResult updateResult = issueService.update(
									user, updateValidationResult);
							if (!updateResult.isValid()) {
								log.error("Could not append '" + toAppend
										+ "' to issue " + mutableIssue.getKey());
							} else {
								log.info("Message '" + toAppend
										+ "' appended to summary of issue "
										+ mutableIssue.getKey());
							}
						} else {
							log.error("Could not append '" + toAppend
									+ "' to issue " + mutableIssue.getKey());
							for (String errMsg : updateValidationResult
									.getErrorCollection().getErrorMessages()) {
								log.error("Validation error : " + errMsg);
							}
						}
					}
				}
			}
		} catch (MessagingException e) {
			log.error("Couldn't read the message subject: " + e.getMessage());
		}
	}

	/**
	 * Given an email address of the form "Arthur Dent <arthur.Dent@earth.com>",
	 * this function returns "arthur.dent@earth.com".
	 * 
	 * @param address
	 * @return The email address, without any surrounding characters
	 */
	public final static String extractEmailAddressOnly(String address) {

		// This regular expression means : match any sequence of
		// * words characters (a-z, 0 to 9, underscore) or dot followed by...
		// * An AT sign (@)
		// * words characters (a-z, 0 to 9, underscore) or dot followed by...
		// * between two to four characters
		Pattern p = Pattern
				.compile("[\\w\\-\\.]+@[\\w\\-\\.]+\\.[a-zA-Z]{2,4}");
		Matcher m = p.matcher(address);

		if (m.find()) {
			return m.group().toLowerCase();
		}

		return null;
	}

	/**
	 * Attaches HTML parts. Comments never wish to keep HTML parts that are not
	 * attachments, as they extract the plain text part and use that as content.
	 * This method therefore is hard wired to always return false.
	 * 
	 * @param part
	 *            the HTML part being processed
	 * @return always false
	 * @throws MessagingException
	 * @throws IOException
	 */
	protected boolean attachHtmlParts(final Part part)
			throws MessagingException, IOException {
		return false;
	}

	/**
	 * Attaches plaintext parts. Plain text parts must be kept if they are not
	 * empty.
	 * 
	 * @param part
	 *            the plain text part being tested
	 * @return true if content is not empty and must be attached
	 */
	protected boolean attachPlainTextParts(final Part part)
			throws MessagingException, IOException {
		return !MailUtils.isContentEmpty(part);
	}

	/**
	 * Return a IssueUtilsBean which is a utility class from Atlassian to
	 * determine available workflow actions for a given user
	 * 
	 * @param u
	 *            The given user
	 * @return An IssueUtilsBean instance
	 */
	private IssueUtilsBean getIssueUtilsBean(User u) {
		final ComponentManager cm = ComponentManager.getInstance();
		final IssueManager im = cm.getIssueManager();
		final WorkflowManager wfm = cm.getWorkflowManager();
		// final PluginAccessor pa = cm.getPluginAccessor();
		final JiraAuthenticationContext jac = cm.getJiraAuthenticationContext();
		final IssueWorkflowManager iwfm = new IssueWorkflowManagerImpl(im, wfm, jac);
		//jac.setUser(u);
		jac.setLoggedInUser(u);

		//IssueUtilsBean iub = new IssueUtilsBean(im, wfm, jac);
		IssueUtilsBean iub = new IssueUtilsBean(wfm, jac, iwfm);

		return iub;
	}

	/**
	 * Progress an issue in the workflow, on behalf of the given user. Note that
	 * this function is quite simple, it does not support setting custom fields
	 * on the issue prior to performing the transition - the only supported
	 * field is the RESOLUTION field.
	 * 
	 * @param issueToSetStatusOn
	 * @param emailValues
	 * @param user
	 * @param workflowTargetName
	 * @param resolutionValue
	 */
	@SuppressWarnings("unchecked")
	public void applyWorkflowTransition(MutableIssue issueToSetStatusOn,
			User user, String workflowTargetName, String resolutionValue) {
		log.debug("Issue: " + issueToSetStatusOn.getGenericValue().toString());
		WorkflowManager mgr = ManagerFactory.getWorkflowManager();
		JiraWorkflow wf = mgr.getWorkflow(issueToSetStatusOn);
		WorkflowTransitionUtil workflowTransitionUtil = JiraUtils
				.loadComponent(WorkflowTransitionUtilImpl.class);

		log.debug("FYI, Issue " + issueToSetStatusOn.getKey()
				+ " is managed by the [" + wf.getName() + "] workflow");

		log.debug("Issue type is '"
				+ (issueToSetStatusOn.getIssueTypeObject() != null ? issueToSetStatusOn
						.getIssueTypeObject().getName() : "NULL !!!") + "'");

		IssueUtilsBean issueUtilsBean = getIssueUtilsBean(user);
		ActionDescriptor targetAction = null;
		Map<Integer, ActionDescriptor> availableActions = issueUtilsBean
				.loadAvailableActions(issueToSetStatusOn);
		for (Iterator<Integer> iterator = availableActions.keySet().iterator(); iterator
				.hasNext();) {
			Integer aKey = iterator.next();
			ActionDescriptor aDescriptor = availableActions.get(aKey);
			log.debug("Checking available action [" + aDescriptor.getId() + ":"
					+ aDescriptor.getName() + "]");
			if (aDescriptor.getName().equalsIgnoreCase(workflowTargetName)) {
				log.debug("Requested workflow step is valid for issue ["
						+ issueToSetStatusOn.getKey() + "] state: ["
						+ aDescriptor.getId() + ":" + aDescriptor.getName()
						+ "]");
				targetAction = aDescriptor;
				break;
			}
		}

		if (targetAction == null) {
			log.error("Workflow Transition had a problem with the given workflow step, it was not in the list of available next steps for the given issues current state");
			return;
		}

		Map<String, Object> workflowTransitionParams = new HashMap<String, Object>();

		workflowTransitionUtil.setIssue(issueToSetStatusOn);
		workflowTransitionUtil.setAction(targetAction.getId());
		workflowTransitionUtil.setUsername(user.getName());

		// Force the issue type id (the workflow engine complains it is missing
		// otherwise )
		Field issueTypeField = ManagerFactory.getFieldManager().getField(
				IssueFieldConstants.ISSUE_TYPE);
		workflowTransitionParams.put(issueTypeField.getId(), issueToSetStatusOn
				.getIssueTypeObject().getId());

		Field resolutionField = ManagerFactory.getFieldManager().getField(
				IssueFieldConstants.RESOLUTION);
		if (resolutionField != null) {
			String resolutionFieldId = resolutionField.getId();
			if (resolutionValue == null || "".equals(resolutionValue.trim())) {
				resolutionValue = "Fixed";
				ConstantsManager cmr = ComponentManager.getInstance()
						.getConstantsManager();
				Collection<Resolution> c = cmr.getResolutionObjects();
				for (Resolution resolution : c) {

					if (resolution.getNameTranslation().equalsIgnoreCase(
							resolutionValue)) {
						log.debug("Matched resolution : "
								+ resolution.getNameTranslation());
						workflowTransitionParams.put(resolutionFieldId,
								resolution.getId());
						break;
					}
				}
			}
		}

		workflowTransitionUtil.setParams(workflowTransitionParams);

		ErrorCollection ecValidate = workflowTransitionUtil.validate();
		if (ecValidate.hasAnyErrors()) {
			log.debug("Workflow transition incomplete, there were "
					+ ecValidate.getErrors().size()
					+ " validate workflow transition errors");
			Map<String, String> validateErrors = ecValidate.getErrors();
			for (Iterator<String> iterator2 = validateErrors.keySet()
					.iterator(); iterator2.hasNext();) {
				String fieldId = iterator2.next();
				String msg = validateErrors.get(fieldId);
				log.error("Workflow Transition (validate) had a problem with the workflow field ["
						+ fieldId
						+ "], value ["
						+ workflowTransitionParams.get(fieldId)
						+ "], message : " + msg);
				return;
			}
		}

		// Execute workflow
		ErrorCollection ecProgress = workflowTransitionUtil.progress();
		if (ecProgress.hasAnyErrors()) {
			log.debug("Workflow transition incomplete, "
					+ ecProgress.getErrors().size()
					+ " progress workflow transition errors");
			Map<String, String> progressErrors = ecProgress.getErrors();
			for (Iterator<String> iterator2 = progressErrors.keySet()
					.iterator(); iterator2.hasNext();) {
				String fieldId = iterator2.next();
				String msg = progressErrors.get(fieldId);
				log.error("Workflow transition (progress) had a problem with the workflow field ["
						+ fieldId + "] : message was " + msg);
				return;
			}
		}

		MutableIssue progressedIssue = ComponentManager.getInstance()
				.getIssueManager().getIssueObject(issueToSetStatusOn.getId());
		log.debug("Workflow transition completed successfully: "
				+ progressedIssue.getGenericValue().toString());
	}

}
