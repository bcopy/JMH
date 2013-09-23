/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package cern.enice.jira.emailhandler;

import java.io.IOException;
import java.util.List;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;

import com.atlassian.jira.JiraApplicationContext;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.IssueFactory;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.util.IssueUpdater;
import com.atlassian.jira.plugins.mail.handlers.AbstractCommentHandler;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.UserUtils;
import com.atlassian.mail.MailUtils;

public class FullCommentHandler extends AbstractCommentHandler
{
    private static final Category log = Logger.getLogger(FullCommentHandler.class);


	private boolean m_registerSenderInCommentText = false;
	
	public FullCommentHandler(){
		   super();
		}
	public FullCommentHandler(PermissionManager pm, IssueUpdater issueUpdater, CommentManager commentManager, IssueFactory issueFactory, ApplicationProperties applicationProperties, JiraApplicationContext jiraApplicationContext){
		//super(pm, issueUpdater, commentManager, issueFactory, applicationProperties, jiraApplicationContext);
		super(pm, issueUpdater, applicationProperties, jiraApplicationContext);
	}
	
	public void setRegisterSenderInCommentText(boolean registerSenderInCommentText) {
		m_registerSenderInCommentText = registerSenderInCommentText;
	}

	/**
     * Given a message, adds the entire message body as a comment to
     * the first issue referenced in the subject.
     */
	protected String getEmailBody(Message message) throws MessagingException
    {
    	String body = MailUtils.getBody(message);
    	if(m_registerSenderInCommentText){
    		
    		List<String> senders = MailUtils.getSenders(message);
    		String firstSender = null;
    		if (senders.size() > 0) {
    			firstSender = senders.get(0);
    		}
    		
    		String commentedVia = "Commented via e-mail ";
            //if (message.getFrom() != null && message.getFrom().length > 0) {
    		if (firstSender != null && firstSender.length() > 0) {
    			commentedVia += "received from: " + message.getFrom()[0];
            } else {
            	commentedVia += "but could not establish sender's address.";
            }
    		
    		if (UserUtils.getUserByEmail(firstSender) == null) {
				body = "{panel:bgColor=yellow}" +
						"*WARNING* - unknown JIRA user - " +
						"it was automatically set to a generic support account.\n\n" +
						commentedVia +
						"{panel}\n\n" + body;
			}

    	}
        return body;
    }

    /**
     * Attaches plaintext parts.  
     * Plain text parts must be kept if they are not empty.
     *
     * @param part  the plain text part.
     * @return  true if the part is not empty, false otherwise
     */
    protected boolean attachPlainTextParts(final Part part) throws MessagingException, IOException
    {
        return !MailUtils.isContentEmpty(part);
    }

    /**
     * Attaches HTML parts.
     * Comments never wish to keep html parts that are not attachments as they extract the plain text
     * part and use that as the content. This method therefore is hard wired to always return false.
     *
     * @param part  the HTML part being processed
     * @return  always false
     * @throws  MessagingException
     * @throws  IOException
     */
    protected boolean attachHtmlParts(final javax.mail.Part part) throws MessagingException, IOException
    {
        return false;
    }
    

}
