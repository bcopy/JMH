package cern.enice.jira.emailhandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;

import com.atlassian.jira.user.UserUtils;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.ManagerFactory;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.IssueImpl;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.SummarySystemField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.mail.MailThreadManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.jira.plugins.mail.handlers.AbstractMessageHandler;

import com.atlassian.jira.util.EasyList;
import com.atlassian.jira.web.bean.FieldVisibilityBean;
import com.atlassian.jira.workflow.WorkflowFunctionUtils;
import com.atlassian.mail.MailUtils;
import com.atlassian.crowd.embedded.api.User;
import com.opensymphony.util.TextUtils;


/**
 * A message handler to create a new issue from an incoming message. 
 * Note: requires public noarg constructor as this
 * class is instantiated by reflection.
 * 
 */
public class AdvancedCreateIssueHandler extends AbstractMessageHandler {

    private static final Logger log = Logger.getLogger(AdvancedCreateIssueHandler.class);
    private IssueDescriptor issueDescriptor;
    public String projectKey = null;         // chosen project for the new issue
    public String issueType;                 // chosen issue type 
    public String defaultProjectKey = null;  // default project where new issues are created
    public String defaultIssueType = null;   // default type for new issues
    public String defaultComponentName = null;   // default component for new issues in the default project
    public boolean ccAssignee = true;        // first Cc'ed user becomes the assignee ?
    private static final String KEY_PROJECT = "project";
    private static final String KEY_ISSUETYPE = "issuetype";
    private static final String CC_ASSIGNEE = "ccassignee";
    private static final String KEY_COMPONENT = "component";

    
    public void init(Map params, MessageHandlerErrorCollector monitor) {
        log.debug("CreateIssueHandler.init(params: " + params + ")");

        super.init(params, monitor);

        if (params.containsKey(KEY_PROJECT)) {
            defaultProjectKey = (String) params.get(KEY_PROJECT);
        }

        if (params.containsKey(KEY_ISSUETYPE)) {
            defaultIssueType = (String) params.get(KEY_ISSUETYPE);
        }

        if (params.containsKey(CC_ASSIGNEE)) {
            ccAssignee = Boolean.valueOf((String) params.get(CC_ASSIGNEE)).booleanValue();
        }
        
        if (params.containsKey(KEY_COMPONENT)) {
        	defaultComponentName = (String) params.get(KEY_COMPONENT);
        }
        
        log.debug("Params: " + defaultProjectKey + " - " + defaultIssueType + " - " + ccAssignee + " - "+ defaultComponentName);
    }
    
    public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException {
        log.debug("AdvancedCreateIssueHandler.handleMessage");
        
        if (issueDescriptor.getIssueType() != null) {   
        	issueType = issueDescriptor.getIssueType();
        } else {
        	issueType = defaultIssueType;
        }
        
        if (!canHandleMessage(message, context.getMonitor())) {
            return deleteEmail;
        }

        try {
        	String reporterName = issueDescriptor.getReporter();
        	// Sets the reporter specified in the tag, or the sender of the message, 
        	// or the default reporter
        	User reporter = (
        			reporterName != null && getUserByName(reporterName) != null ?
        					getUserByName(reporterName) :
       						getReporter(message, context)	
        	);
            if (reporter == null) {
                String errorMessage = "Sender is anonymous, no default reporter specified and creating users " +
                        "is set to false (or external user managment is enabled). Message rejected.";
                log.warn(errorMessage);
                //addError(errorMessage);
                return false;
            }

            Project project = getProject();
            log.debug("Project = " + project);
            boolean usingDefaultProject = false;
            if (project == null) {
            	project = getProjectManager().getProjectObjByKey(defaultProjectKey.toUpperCase());
            	if(! (project == null) ) {
            		usingDefaultProject = true;
            	}
            }
            if (project == null) {
                String errorMessage = "Cannot handle message as destination project is null and no default project set";
                log.warn(errorMessage);
                //addError(errorMessage);
                return false;
            }
            
            log.debug("Issue Type Key = = " + issueType);
            if (!hasValidIssueType()) {
                String errorMessage = "Cannot handle message as Issue Type is null or invalid";
                log.warn(errorMessage);
                //addError(errorMessage);
                return false;
            }
            String summary = issueDescriptor.getSummary();   
            if (!TextUtils.stringSet(summary)) { // should never happen
                //addError("Issue must have a summary. The mail message has an empty or no subject.");
                return false;
            }
            if (summary.length() > SummarySystemField.MAX_LEN.intValue()) {
                log.warn("Truncating summary field because it is too long: " + summary);
                summary = summary.substring(0, SummarySystemField.MAX_LEN.intValue() - 3) + "...";
            }

            // JRA-7646 - check if priority/description is hidden - if so, do not set
            String priority = null;
            String description = null;
            FieldVisibilityBean visibility = new FieldVisibilityBean();

            if (!visibility.isFieldHiddenInAllSchemes(project.getId(), IssueFieldConstants.PRIORITY, EasyList.build(issueType))) {
            	if(issueDescriptor.getPriorityId() != null){
                  priority = issueDescriptor.getPriorityId().toString();
            	}else{
            	  priority = getDefaultSystemPriority();
            	}
            }

            if (!visibility.isFieldHiddenInAllSchemes(project.getId(), IssueFieldConstants.DESCRIPTION, EasyList.build(issueType))) {
                description = getDescription(reporter, message);
            }

            MutableIssue issueObject = ComponentManager.getInstance().getIssueFactory().getIssue();
            
            issueObject.setProjectId(project.getId());
            issueObject.setSummary(summary);
            issueObject.setDescription(description);
            issueObject.setIssueTypeId(issueType);
            issueObject.setReporter(reporter);
	                
	    if (issueDescriptor.getDueDate() != null) {
	       issueObject.setDueDate(issueDescriptor.getDueDate());
	    }
	    if (issueDescriptor.getOriginalEstimate() != null) {
	       issueObject.setOriginalEstimate(issueDescriptor.getOriginalEstimate());
	    }

            // Sets the assignee specified in the tag, or the first valid Cc'ed assignee, 
            // or else the default assignee
            String assigneeName = issueDescriptor.getAssignee();
            User assignee = null;
            if (
            		assigneeName != null && 
            		getUserByName(assigneeName) != null && 
            		isValidAssignee(project, getUserByName(assigneeName))) {
            	assignee = getUserByName(assigneeName);
            } else {
            	if (ccAssignee) {
            		assignee = getFirstValidAssignee(message.getAllRecipients(), project);
            	}
            }
            if (assignee == null) {
                assignee = ComponentManager.getInstance().getAssigneeResolver().getDefaultAssignee(issueObject, Collections.EMPTY_MAP);
            }
            if (assignee != null) {
                issueObject.setAssignee(assignee);
            }
            
            issueObject.setPriorityId(priority);
            
            // Sets components to the issue
            if (issueDescriptor.getComponents() != null) {   // The component name
            	String[] comps = issueDescriptor.getComponents();
        		Collection components = new ArrayList();
            	for (int i = 0; i < comps.length; i++) {
            		
            		GenericValue component = getProjectManager().getComponent(project.getGenericValue(), comps[i]);
            		if (component != null) {
            			components.add(component);
            		}
            	}
            	if (components.size() > 0) {
            		issueObject.setComponents(components);
            	}
            } else {
            	// If no component was specified and we are using the default project
            	// Try and set the default component
                GenericValue defaultComponent = null;
                if(usingDefaultProject && project != null){
                	// Try and set a default component too
                	defaultComponent = getProjectManager().getComponent(project.getGenericValue(),defaultComponentName);
                	if(defaultComponent == null){
                		String errorMessage = "Cannot set default component on new issue as component does not exist";
                        log.info(errorMessage);
                	}
                }
                
                if(defaultComponent != null){
                  issueObject.setComponents(Arrays.asList(new GenericValue[]{defaultComponent}));
                }
            	
            }

            // Ensure issue level security is correct
            setDefaultSecurityLevel(issueObject);

            Map fields = new HashMap();
            fields.put("issue", issueObject);
            GenericValue originalIssueGV = ComponentManager.getInstance().getIssueManager().getIssue(issueObject.getId());

            // Give the CustomFields a chance to set their default values JRA-11762
            List customFieldObjects = ComponentManager.getInstance().getCustomFieldManager().getCustomFieldObjects(issueObject);
            for (Iterator iterator = customFieldObjects.iterator(); iterator.hasNext();) {
                CustomField customField = (CustomField) iterator.next();
                issueObject.setCustomFieldValue(customField,  customField.getDefaultValue(issueObject));
            }

            fields.put(WorkflowFunctionUtils.ORIGNAL_ISSUE_KEY, IssueImpl.getIssueObject(originalIssueGV));
            GenericValue issue = ManagerFactory.getIssueManager().createIssue(reporter, fields);

            if (issue != null) {
                log.info("Issue " + issue.get("key") + " created");

                // Record the message id of this e-mail message so we can track replies to this message
                // and associate them with this issue
                recordMessageId(MailThreadManager.ISSUE_CREATED_FROM_EMAIL, message, issue.getLong("id"), context);
            }

            createAttachmentsForMessage(message, issueObject, context);

            return true;
        }
        catch (CreateException e) {
        	// if CreateException appeared send email to the user saying he doesn't have a permission to create issues 
        	log.error("USER DOESNT HAVE PERMISSIONS TO CREATE ISSUE", e);
        } catch (Exception e) {
            String errorMessage = "Could not create issue!";
            log.error(errorMessage, e);
            //addError(errorMessage, e);
        }

        // something went wrong - don't delete the message
        return false;
    }

    
    protected Project getProject() {
    	String pkey = issueDescriptor.getProjectKey();
    	if (
    			pkey != null && 
    			!pkey.equals("") &&
    			getProjectManager().getProjectObjByKey(pkey.toUpperCase()) != null) {
    		return getProjectManager().getProjectObjByKey(pkey.toUpperCase());    	
    	} else {
    		return null;
    	}
    }

    
    protected boolean hasValidIssueType() {
        // if there is no project then the issue cannot be created
        if (issueType == null) {
            log.debug("Issue Type NOT set. Cannot find Issue type.");
            return false;
        }

        IssueType issueTypeObject = ManagerFactory.getConstantsManager().getIssueTypeObject(issueType);
        if (issueTypeObject == null) {
            log.debug("Issue Type does not exist with id of " + issueType);
            return false;
        }

        log.debug("Issue Type Object = " + issueTypeObject.getName());
        return true;
    }

    
    protected ProjectManager getProjectManager() {
        return ManagerFactory.getProjectManager();
    }

    
    /**
     * Extracts the description of the issue from the message.
     * 
     * @param reporter  the established reporter of the issue
     * @param message  the message from which the issue is created
     * @return the description of the issue
     * @throws MessagingException
     */
    private String getDescription(User reporter, Message message) throws MessagingException {
        return recordFromAddressForAnon(reporter, message, MailUtils.getBody(message));
    }

    
    /**
     * Adds the senders' From: addresses to the end of the issue's details (if they could be extracted), if the e-mail
     * has been received from an unknown e-mail address and the mapping to an "anonymous" user has been enabled.
     * 
     * @param reporter  the established reporter of the issue (after one has been established)
     * @param message  the message that is used to create issue
     * @param description  the issues extracted description
     * @return the modified description if the e-mail is from anonymous user, unmodified description otherwise
     * @throws MessagingException
     */
    private String recordFromAddressForAnon(User reporter, Message message, String description) throws MessagingException {
    	
    	String createdVia = "";
    	
    	List<String> senders = MailUtils.getSenders(message);
		String firstSender = null;
		if (senders.size() > 0) {
			firstSender = senders.get(0);
		}
		
		if (reporteruserName != null && reporteruserName.equals(reporter.getName())) {
        	
			createdVia = "\n\nCreated via e-mail ";
            if (message.getFrom() != null && message.getFrom().length > 0) {
            	createdVia += "received from: " + message.getFrom()[0];
            } else {
            	createdVia += "but could not establish sender's address.";
            }
        }
	
		if (UserUtils.getUserByEmail(firstSender) == null) {
			description = "{panel:bgColor=yellow}" +
					"*WARNING* - the issue REPORTER was not initially a known JIRA user - " +
					"it was automatically set to a generic support account, please correct this as necessary." +
					createdVia +
					"{panel}\n\n" + description;
		}
    	
        // If the message has been created for an anonymous user add the senders e-mail address to the description.
        
        return description;
    }

    

    /**
     * @deprecated This method calls deprecated JIRA methods - update it soon !
     */
    private String getDefaultSystemPriority() {
        // if priority header is not set, assume it's 'default'
        ConstantsManager constantsManager = ManagerFactory.getConstantsManager();
        GenericValue defaultPriority = constantsManager.getDefaultPriority();
        if (defaultPriority != null) {
            return defaultPriority.getString("id");
        } else {
            log.error("Default priority was null. Using the 'middle' priority.");

            Collection priorities = constantsManager.getPriorities();
            Iterator priorityIt = priorities.iterator();
            int times = (int) Math.ceil((double) priorities.size() / 2d);
            for (int i = 0; i < times; i++) {
                defaultPriority = (GenericValue) priorityIt.next();
            }

            return defaultPriority.getString("id");
        }
    }

    
    /**
     * Given an array of addresses, returns the first valid assignee for the appropriate project.
     * 
     * @param addresses  the addresses
     * @param project  the project
     * @return  the first valid assignee for <code>project</code>
     */
    public static User getFirstValidAssignee(Address[] addresses, Project project)
    {
        if (addresses == null || addresses.length == 0) {
            return null;
        }

        for (int i = 0; i < addresses.length; i++) {
            if (addresses[i] instanceof InternetAddress) {
                InternetAddress email = (InternetAddress) addresses[i];

                User validUser = UserUtils.getUserByEmail(email.getAddress());
                
                if (validUser != null) {
                	log.error("The user meant no be an assignee is unknown.");
                } else {
	                if (isValidAssignee(project, validUser)) {
	                	return validUser;
	                }
                }
            }
        }

        return null;
    }


	/**
	 * Tells if <code>user</code> is a valid assignee for <code>project</code>. 
	 * 
	 * @param project  a project
	 * @param user  an user
	 * @return  whether <code>user</code> is a valid assignee for <code>project</code>
	 */
    private static boolean isValidAssignee(Project project, User user) {
    	return (ManagerFactory.getPermissionManager().hasPermission(Permissions.ASSIGNABLE_USER, project, user));	
    }
    
    
    /**
     * Returns an <code>User</code> given its <code>userName</code>.
     * 
     * @param userName  the name of the user
     * @return  the user, or <code>null</code> if there was no user with name <code>userName</code>
     */
    public static User getUserByName(String userName) {
		return UserUtils.getUser(userName);
    }
    
    
    private void setDefaultSecurityLevel(MutableIssue issue) throws Exception {
        GenericValue project = issue.getProject();
        if (
//        		isEnterprise() && 
        		project != null) {
            IssueSecurityLevelManager issueSecurityLevelManager = ManagerFactory.getIssueSecurityLevelManager();
            final Long levelId = issueSecurityLevelManager.getSchemeDefaultSecurityLevel(project);
            if (levelId != null) {
                issue.setSecurityLevel(issueSecurityLevelManager.getIssueSecurity(levelId));
            }
        }
    }

    
//    private boolean isEnterprise() {
//        License license = LicenseManager.getInstance().getLicense(JiraLicenseUtils.JIRA_LICENSE_KEY);
//        return (license != null && license.isLicenseLevel(EasyList.build(JiraLicenseUtils.JIRA_ENTERPRISE_LEVEL)));
//    }

    
	final void setIssueDescriptor(IssueDescriptor issueDescriptor) {
		this.issueDescriptor = issueDescriptor;
	}


    /**
     * Attaches plaintext parts.  
     * Text parts are not attached but rather potentially form the source of issue text.
     * However text part attachments are kept providing they are not empty.
     *
     * @param part  the part which will have a content type of text/plain to be tested
     * @return  true if the part is an attachment and not empty
     * @throws  MessagingException
     * @throws  IOException
     */
    protected boolean attachPlainTextParts(final Part part) throws MessagingException, IOException
    {
        return !MailUtils.isContentEmpty(part) && MailUtils.isPartAttachment(part);
    }

    /**
     * Attaches HTML parts. 
     * HTML parts are not attached but rather potentially form the source of issue text.
     * However html part attachments are kept providing they are not empty.
     *
     * @param part  the part which will have a content type of text/html to be tested
     * @return  true if the part is an attachment and not empty
     * @throws  MessagingException
     * @throws  IOException
     */
    protected boolean attachHtmlParts(final Part part) throws MessagingException, IOException
    {
        return !MailUtils.isContentEmpty(part) && MailUtils.isPartAttachment(part);
    }

}
