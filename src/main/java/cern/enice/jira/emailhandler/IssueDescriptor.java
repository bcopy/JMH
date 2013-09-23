package cern.enice.jira.emailhandler;

import java.sql.Timestamp;

/**
 * Description of an issue in JIRA as it was parsed from an email message.
 * <p>Released under the BSD License: see file license.txt for details. 
 * 
 */
public interface IssueDescriptor {

	
	/**
	 * @return the components associated to this issue
	 */
	public String[] getComponents();

	/**
	 * @return the issue type
	 */
	public String getIssueType();

	/**
	 * @return the priority
	 */
	public String getPriorityId();

	/**
	 * @return the project key
	 */
	public String getProjectKey();

	/**
	 * @return the summary of this issue
	 */
	public String getSummary();
	
	/**
	 * @return the reporter of this issue
	 */
	public String getReporter();
	
	/**
	 * @return the assignee of this issue 
	 */
	public String getAssignee();
	
	/**
	 * @return the due date of this issue
	 */
	public Timestamp getDueDate();
	
	/**
	 * @return the original estimate for the issue
	 */
	public Long getOriginalEstimate();

	/**
	 * @return The target workflow step to transition the issue to
	 */
	public String getWorkflowTarget();
	
	/**
	 * @return The resolution to use for the issue
	 */
	public String getResolution();
}
