package cern.enice.jira.emailhandler;

import java.sql.Timestamp;

/**
 * Implementation of an {@link IssueDescriptor}.
 * <p>Released under the BSD License: see file license.txt for details. 
 * 
 */
public class IssueDescriptorImpl implements IssueDescriptor {

	private String projectKey = null;
	private String summary = new String("");
	private String issueType = null;         
	private String m_priorityId = null;             
	private String[] components = null;    
	private String reporter = null;
	private String assignee = null;
	private Timestamp dueDate = null;
	private Long originalEstimate = null;
	
	private String workflowTarget = null;
	private String resolution = null;

	
	public String getWorkflowTarget() {
		return workflowTarget;
	}


	public void setWorkflowTarget(String workflowTarget) {
		this.workflowTarget = workflowTarget;
	}


	public String getResolution() {
		return resolution;
	}


	public void setResolution(String resolution) {
		this.resolution = resolution;
	}


	public IssueDescriptorImpl() {
		// Empty constructor
	}


	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getComponents()
	 */
	public String[] getComponents() {
		return this.components;
	}
	
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getIssueType()
	 */
	public String getIssueType() {
		return this.issueType;
	}
	
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getPriorityId()
	 */
	public String getPriorityId() {
		return this.m_priorityId;
	}
	
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getProjectKey()
	 */
	public String getProjectKey() {
		return this.projectKey;
	}
	
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getSummary()
	 */
	public String getSummary() {
		return this.summary;
	}


	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getAssignee()
	 */
	public String getAssignee() {
		return this.assignee;
	}


	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getReporter()
	 */
	public String getReporter() {
		return this.reporter;
	}
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getDueDate()
	 */
	public Timestamp getDueDate() {
		return this.dueDate;
	}
	
	/**
	 * @see cern.jira.emailhandler.IssueDescriptor#getOriginalEstimate()
	 */
	public Long getOriginalEstimate() {
		return this.originalEstimate;
	}

	/**
	 * @param components  the components to set
	 */
	public void setComponents(String[] components) {
		this.components = components;
	}


	/**
	 * @param issueType  the issue type to set
	 */
	public void setIssueType(String issueType) {
		this.issueType = issueType;
	}


	/**
	 * @param m_priorityId  the priority to set
	 */
	public void setPriority(String priorityId) {
		this.m_priorityId = priorityId;
	}


	/**
	 * @param projectKey  the project key to set
	 */
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}


	/**
	 * @param summary  the summary to set
	 */
	public void setSummary(String summary) {
		this.summary = summary;
	}


	/**
	 * @param reporter  the reporter to set
	 */
	public void setReporter(String reporter) {
		this.reporter = reporter;
	}


	/**
	 * @param assignee  the assignee to set
	 */
	public void setAssignee(String assignee) {
		this.assignee = assignee;
	}
	
	/**
	 * @param dueDate  the due date to set
	 */
	public void setDueDate(Timestamp dueDate) {
		this.dueDate = dueDate;
	}
	
	/**
	 * @param originalEstimate  the original estimate to set
	 */
	public void setOriginalEstimate(Long originalEstimate) {
		this.originalEstimate = originalEstimate;
	}

	
}

