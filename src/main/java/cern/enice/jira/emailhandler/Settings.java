package cern.enice.jira.emailhandler;


/**
 * Settings and string constants for the email handler.
 * <p>Released under the BSD License: see file license.txt for details. 
 */
public class Settings {

	/** Default issue summary, if the user specified an empty summary. */
	public static final String DEFAULT_SUMMARY = "(no summary)";
	
	public static final String REGEX_ISSUETYPE_BUG = "#BUG";                   // "1" 
	public static final String REGEX_ISSUETYPE_NEW_FEATURE = "#NEWFEATURE";    // "2"
	public static final String REGEX_ISSUETYPE_TASK = "#TASK";                 // "3"
	public static final String REGEX_ISSUETYPE_IMPROVEMENT = "#IMPROVEMENT";   // "4" 
	public static final String REGEX_ISSUETYPE_SUBTASK = "#SUBTASK";           // "5"

	public static final String REGEX_PRIORITY_BLOCKER = "#BLOCKER";
	public static final String REGEX_PRIORITY_CRITICAL = "#CRITICAL";
	public static final String REGEX_PRIORITY_MAJOR = "#MAJOR";  
	public static final String REGEX_PRIORITY_MINOR = "#MINOR";
	public static final String REGEX_PRIORITY_TRIVIAL = "#TRIVIAL";
	
	public static final String REGEX_WORKFLOW_TARGET = "#WORKFLOW=";     
	public static final String REGEX_WORKFLOW_RESOLVE = "#RESOLVE";     
	public static final String REGEX_WORKFLOW_CLOSE = "#CLOSE";
	public static final String REGEX_WORKFLOW_RESOLUTION = "#RESOLUTION=";

	public static final String REGEX_COMPONENT = "#COMPONENT=";

	public static final String REGEX_PROJECTKEY = "#PROJECT=";
	
	public static final String REGEX_ASSIGNEE = "#ASSIGNEE=";

	public static final String REGEX_REPORTER = "#REPORTER=";
	
	public static final String REGEX_DUEDATE = "#DUE=";
	
	public static final String REGEX_ESTIMATE = "#EST=";
	
}

