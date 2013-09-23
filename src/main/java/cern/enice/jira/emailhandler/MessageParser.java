package cern.enice.jira.emailhandler;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.atlassian.core.util.DateUtils;
import com.atlassian.core.util.InvalidDurationException;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.priority.Priority;

/**
 * A class containing some useful method to parse a message.
 * <p>Released under the BSD License: see file license.txt for details. 
 * 
 */
public class MessageParser {

	private static final Category log = Logger.getLogger(MessageParser.class);
	
	/** Regex for "any number of characters, even zero". */
	public static final String REGEX_ANYCHAR = ".*";

	
	/**
	 * Parses a message that contains directives about an issue.
	 * 
	 * @param message  the message to parse
	 * @param emailAddresses  an array defining all JIRA email addresses
	 * @return  a descriptor of the issue, following the directives extracted from <code>message</code>
	 */
	public final static IssueDescriptor parse(final Message message, final String[] emailAddresses) {
		IssueDescriptorImpl issue = new IssueDescriptorImpl();
		String toHeader = null;
		try {
			projectkey:
				for (int i = 0; i < emailAddresses.length; i++) {
					if (emailAddresses[i] == null) continue;
					toHeader = getRecipientFromMessage(emailAddressToRegex(emailAddresses[i]), message);
					if (toHeader != null) {
						issue.setProjectKey(getFullnameFromHeader(emailAddresses[i], toHeader));
						break projectkey;
					}
				}
			// Project key has been recorded in the issue.
			// Note that at this time the project key may also be invalid, 
			// but may be overridden if a tag Settings.REGEX_PROJECTKEY
			// is found in the message subject.
			if (toHeader == null) {
				// This should never happen as in this case Jira is supposed 
				// not having received the message
				log.error("Jira email address not found amongst recipients.");
				return issue;				
			}
		} catch (MessagingException e) {
			log.error("Error while parsing the message: ", e);
			return issue;
		}
		try {
			parseSubject(message.getSubject(), issue);
		} catch (MessagingException e) {
			log.error("Error parsing message subject: ", e);
			return issue;
		}
		return issue;
	}
	
	
    /**
     * Extracts issue attributes from the email <code>subject</code> and assign them to 
     * <code>issue</code>.
     */
	private final static void parseSubject(final String subject, final IssueDescriptorImpl issueDescriptor) {
		String summary = new String("");
        
        if(subject == null){
            // The subject is null, nothign to parse here !
            issueDescriptor.setSummary(Settings.DEFAULT_SUMMARY);
            return;
        }
        
    	String[] tokens = subject.split("\\s");
    	
    	String estimateString;
    	Pattern estimatePattern = Pattern.compile("^\\d*$");;
    	Matcher estimateMatcher;
    	
    	for (int i = 0; i < tokens.length; i++) {
    		// Check if token matches possible tags.
    		// Issue Type and Priority checks are in increasing importance
    		if (tokens[i].matches(Settings.REGEX_ISSUETYPE_IMPROVEMENT)) {
    			issueDescriptor.setIssueType("4");        			
    		}
    		else if (tokens[i].matches(Settings.REGEX_ISSUETYPE_SUBTASK)) {
    			issueDescriptor.setIssueType("5");        			
    		}
    		else if (tokens[i].matches(Settings.REGEX_ISSUETYPE_TASK)) {
    			issueDescriptor.setIssueType("3");        			
    		}
    		else if (tokens[i].matches(Settings.REGEX_ISSUETYPE_NEW_FEATURE)) {
    			issueDescriptor.setIssueType("2");        			
    		}
    		else if (tokens[i].matches(Settings.REGEX_ISSUETYPE_BUG)) {
    			issueDescriptor.setIssueType("1");
    		}
    		else if (tokens[i].matches(Settings.REGEX_PRIORITY_TRIVIAL)) {
    			setPriorityIdForName(issueDescriptor, IssueFieldConstants.TRIVIAL_PRIORITY);
    		}
    		else if (tokens[i].matches(Settings.REGEX_PRIORITY_MINOR)) {
    			setPriorityIdForName(issueDescriptor, IssueFieldConstants.MINOR_PRIORITY);
    		}
    		else if (tokens[i].matches(Settings.REGEX_PRIORITY_MAJOR)) {
    			setPriorityIdForName(issueDescriptor, IssueFieldConstants.MAJOR_PRIORITY);
    		}
    		else if (tokens[i].matches(Settings.REGEX_PRIORITY_CRITICAL)) {
    			setPriorityIdForName(issueDescriptor, IssueFieldConstants.CRITICAL_PRIORITY);
    		}
    		else if (tokens[i].matches(Settings.REGEX_PRIORITY_BLOCKER)) {
    			setPriorityIdForName(issueDescriptor, IssueFieldConstants.BLOCKER_PRIORITY);
    		}
    		else if (tokens[i].matches(Settings.REGEX_COMPONENT + MessageParser.REGEX_ANYCHAR)) {
    			String manyComponents = tokens[i].replaceFirst(Settings.REGEX_COMPONENT, "");
    			String[] components = manyComponents.split(",");
    			for (int j = 0; j < components.length; j++) {
    				components[j] = components[j].replaceAll("__", " ");    				
    			}
    	    	issueDescriptor.setComponents(components);
    		}
    		else if (tokens[i].matches(Settings.REGEX_PROJECTKEY + MessageParser.REGEX_ANYCHAR)) {
    			issueDescriptor.setProjectKey(tokens[i].replaceFirst(Settings.REGEX_PROJECTKEY, ""));
    			// overrides project key set in recipient email address
    		}
    		else if (tokens[i].matches(Settings.REGEX_REPORTER + MessageParser.REGEX_ANYCHAR)) {
    			issueDescriptor.setReporter(tokens[i].replaceFirst(Settings.REGEX_REPORTER, ""));
    		}
    		else if (tokens[i].matches(Settings.REGEX_ASSIGNEE + MessageParser.REGEX_ANYCHAR)) {
    			issueDescriptor.setAssignee(tokens[i].replaceFirst(Settings.REGEX_ASSIGNEE, ""));
    		}
    		else if (tokens[i].matches(Settings.REGEX_DUEDATE + MessageParser.REGEX_ANYCHAR)) {
    			try {
    				issueDescriptor.setDueDate(new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(tokens[i].replaceFirst(Settings.REGEX_DUEDATE, "")).getTime()));
    			}
    			catch (ParseException e)
    			{
    			}
    		}
    		else if (tokens[i].matches(Settings.REGEX_ESTIMATE + MessageParser.REGEX_ANYCHAR)) {
    			estimateString = tokens[i].replaceFirst(Settings.REGEX_ESTIMATE, "");
    			estimateMatcher = estimatePattern.matcher(estimateString);
    			if (estimateMatcher.matches()) {
    				issueDescriptor.setOriginalEstimate(new Long(estimateString));
    			}
    			else {
    				try {
    					issueDescriptor.setOriginalEstimate(new Long(DateUtils.getDuration(estimateString)));
    				}
    				catch (InvalidDurationException e) {
    				}
    			}
    		}
    		else if (tokens[i].matches(Settings.REGEX_WORKFLOW_TARGET + MessageParser.REGEX_ANYCHAR)) {
    			issueDescriptor.setWorkflowTarget(tokens[i].replaceFirst(Settings.REGEX_WORKFLOW_TARGET, ""));
    		}
    		else if (tokens[i].matches(Settings.REGEX_WORKFLOW_RESOLVE)) {
    			issueDescriptor.setWorkflowTarget("Resolve Issue");
    		}
    		else if (tokens[i].matches(Settings.REGEX_WORKFLOW_CLOSE)) {
    			issueDescriptor.setWorkflowTarget("Close Issue");
    		}
    		else if (tokens[i].matches(Settings.REGEX_WORKFLOW_RESOLUTION + MessageParser.REGEX_ANYCHAR)) {
    			issueDescriptor.setResolution(tokens[i].replaceFirst(Settings.REGEX_WORKFLOW_RESOLUTION, ""));
    		}
    		
    		else {   // Token is not a tag, then add it to the summary text 
    			summary += (tokens[i] + " ");
    		}
    	}
    	summary = summary.trim();
    	issueDescriptor.setSummary(summary.equals("") ? Settings.DEFAULT_SUMMARY : summary);
    }


	/**
	 * Set the priority ID on given issue - we try and look up the given priority value.
	 * If it does not exist, we log an exception, the issue will receive the default priority.
	 * Prior to JMH-16, the priority value was used to estimate an approximate priority level.
	 * We now look up the exact priority name (just a bit more accurate, especially when
	 * JIRA administrators define multiple levels of custom priorities).
	 * 
	 * @param issueDescriptor
	 * @param priorityValue
	 */
	private static void setPriorityIdForName(IssueDescriptorImpl issueDescriptor,String priorityValue) {
		try{
			ConstantsManager cmr = ComponentManager.getInstance()
			.getConstantsManager();
			String priorityId = null;
			Collection<Priority> priorities = cmr.getPriorityObjects();
			for (Priority priority : priorities) {
				if(priority.getName().equalsIgnoreCase(priorityValue)){
					priorityId = priority.getId();
				}
			}
			if(priorityId == null){
				throw new IllegalArgumentException("Could not identify the JIRA Priority ID for priority name '"+priorityValue+"'");
			}
			issueDescriptor.setPriority(priorityId);
		}catch(Exception e){
			log.debug("Could not set priority value "+e.getMessage()+" on new issue ",e);
		}
		
		
	}

    
    /**
     * Retrieves the To:, CCc:, or Bcc: header purporting to a given recipient of a email message.
     * 
     * @param recipientRegExp  a regex describing the recipient email address  
     * @param message  an email message
     * @return  the header of <code>message</code> containing <code>recipient</code>, 
     * or <code>null</code> if the <code>recipient</code> was not found 
     * @throws MessagingException  if there were problems handling the message
     */
    public final static String getRecipientFromMessage(final String recipientRegExp, final Message message) throws MessagingException {
    	Address[] addresses = message.getAllRecipients();
    	for (int i = 0; i < addresses.length; i++) {
    		log.debug("Parsing message address " + i + ": " + addresses[i]);
    		Pattern p = Pattern.compile(recipientRegExp, Pattern.CASE_INSENSITIVE);
    		Matcher m = p.matcher(addresses[i].toString());
    		if (m.find()) {
    			return addresses[i].toString(); 
    		}
    	}
    	return null;
    }
    
    
    /**
     * Extract the "Full Name" part from a full email address (which could be
     * contained in a From:, To:, Cc:, or Bcc: header). 
     * E.g. if <code>fullEmail</code> is "Arthur Dent &lt;arthur@vogon.org&gt;" and 
     * <code>emailAddress</code> is "arthur@vogon\\.org", it returns "Arthur Dent".
     * This method correctly manages the fact that the emailclient could surround 
     * email address and/or full name with characters like <code><> " ' () </code>, etc. 
     * 
     * @param emailAddress  a regex specifying the bare email address to extract
     * @param fullEmail  the full email address
     * @return  the Full Name
     */
    public final static String getFullnameFromHeader(final String emailAddress, final String fullEmail) {
    	String[] tokensToDelete = {emailAddress, "<", ">", "\"", "\'", "\\(", "\\)"};
    	String fullName = new String(fullEmail);
    	for (int i = 0; i < tokensToDelete.length; i++) {
    		fullName = fullName.replaceAll(tokensToDelete[i], "");
    	}
    	fullName = fullName.trim();
		log.debug("Extracted from header = " + fullEmail + " the fullName = " + fullName);
    	return fullName;
    }
    
    
    /**
     * Transforms an email address into a regular expression matching that address.
     * 
     * @param emailAddress  an email address of kind <code>user.login@domain.xx</code> 
     * @return  a regex matching <code>emailAddress</code>
     */
    public final static String emailAddressToRegex(final String emailAddress) {
    	StringBuffer regex = new StringBuffer(".*");
    	for (int c = 0; c < emailAddress.length(); c++) {
    		if (emailAddress.charAt(c) == '.') {
    			regex.append("\\.");
    		} else {
    			regex.append(emailAddress.charAt(c));
    		}
    	}
    	return (regex + ".*").toString();
    }

    
}

