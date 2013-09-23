/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package cern.enice.jira.emailhandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;

import com.atlassian.core.util.ClassLoaderUtils;
import com.atlassian.jira.JiraApplicationContext;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.IssueFactory;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.util.IssueUpdater;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.plugins.mail.handlers.AbstractCommentHandler;
import com.atlassian.mail.MailUtils;

/**
 * This handler adds the body of the email as a comment, using the subject
 * to determine which issue to add the comment to.<br>
 * The difference between this and FullCommentHandler is that this will
 * strip any quoted lines from the email (i.e. lines that start with &gt; or |).
 *
 * @see FullCommentHandler
 */
public class NonQuotedCommentHandler extends AbstractCommentHandler
{
    private static final Logger log = Logger.getLogger(NonQuotedCommentHandler.class);
    private static final String OUTLOOK_QUOTED_FILE = "outlook-email.translations";
    private Collection messages;

	private boolean m_registerSenderInCommentText = false;
	
	public NonQuotedCommentHandler(){
	   super();
	}
	public NonQuotedCommentHandler(PermissionManager pm, IssueUpdater issueUpdater, CommentManager commentManager, IssueFactory issueFactory, ApplicationProperties applicationProperties, JiraApplicationContext jiraApplicationContext){
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
    		body = body + "\n[Commented via e-mail ";
            if (message.getFrom() != null && message.getFrom().length > 0) {
            	body = body + "received from: " + message.getFrom()[0] + "]";
            } else {
            	body += "but could not establish sender's address.]";
            }

    	}

        return stripQuotedLines(body);
    }

    /**
     * Given an email body, strips quoted lines and the 'attribution' line that most mailers
     * prepend (eg. "On Wed 21 Oct 2004, Joe Bloggs wrote:").
     */
    public String stripQuotedLines(String body)
    {
        if (body == null)
            return null;

        StringTokenizer st = new StringTokenizer(body, "\n", true);
        StringBuffer result = new StringBuffer();

        boolean strippedAttribution = false; // set to true once the attribution has been encountered
        boolean outlookQuotedLine = false; // set to true if the Microsoft Outlook reply message ("----- Original Message -----") is encountered.

        String line1 = null;
        String line2 = null;
        String line3 = null;
        // Three-line lookahead; on each iteration, line1 may be added unless line2+line3 indicate it is an attribution
        do
        {
            line1 = line2;
            line2 = line3;
            line3 = (st.hasMoreTokens() ? st.nextToken() : null); // read next line
            if (!"\n".equals(line3))
            {
                // Ignore the newline ending line3, if line3 isn't a newline on its own
                if (st.hasMoreTokens()) st.nextToken();
            }
            if (!strippedAttribution)
            {
                if (!outlookQuotedLine)
                    outlookQuotedLine = isOutlookQuotedLine(line1);

                // Found our first quoted line; the attribution line may be line1 or line2
                if (isQuotedLine(line3))
                {
                    if (looksLikeAttribution(line1))
                        line1 = "> ";
                    else if (looksLikeAttribution(line2)) line2 = "> ";
                    strippedAttribution = true;
                }
            }
            if (line1 != null && !isQuotedLine(line1) && !outlookQuotedLine)
            {
                result.append(line1);
                if (!"\n".equals(line1)) result.append("\n");
            }
        } while (!(line1 == null && line2 == null && line3 == null));
        return result.toString();
    }

    private boolean looksLikeAttribution(String line)
    {
        if (line != null && (line.endsWith(":") || line.endsWith(":\r"))) return true;
        return false;
    }

    private boolean isQuotedLine(String line)
    {
        if (line != null && (line.startsWith(">") || line.startsWith("|")))
            return true;

        return false;
    }

    private boolean isOutlookQuotedLine(String line)
    {
        if (line != null)
        {
            for (Iterator iterator = getOutlookQuoteSeparators().iterator(); iterator.hasNext();)
            {
                String message = (String) iterator.next();
                if (line.indexOf(message) != -1)
                    return true;
            }
        }

        return false;
    }

    private Collection getOutlookQuoteSeparators()
    {
        if (messages == null)
        {
            messages = new LinkedList();
            BufferedReader reader = null;
            try
            {
                // The file is assumed to be UTF-8 encoded.
                reader = new BufferedReader(new InputStreamReader(ClassLoaderUtils.getResourceAsStream(OUTLOOK_QUOTED_FILE, this.getClass()), "UTF-8"));
                String message = null;
                while ((message = reader.readLine()) != null)
                {
                    messages.add(message);
                }
            }
            catch (IOException e)
            {
                // no more properties
                log.error("Error occurred while reading file '" + OUTLOOK_QUOTED_FILE + "'.");
            }
            finally
            {
                try
                {
                    if (reader != null)
                        reader.close();
                }
                catch (IOException e)
                {
                    log.error("Could not close the file '" + OUTLOOK_QUOTED_FILE + "'.");
                }
            }
        }

        return messages;
    }

    /**
     * Attaches plaintext parts. Plain text parts must be kept if they are not empty.
     *
     * @param part  the part being tested
     * @return  true if the part content is not empty, false otherwise
     */
    protected boolean attachPlainTextParts(final Part part) throws MessagingException, IOException
    {
        return !MailUtils.isContentEmpty(part);
    }

    /**
     * Attaches HTML parts. 
     * Comments never wish to keep HTML parts that are not attachments as they extract the plain text
     * part and use that as the content. This method therefore is hard wired to always return false.
     *
     * @param part  the part being tested
     * @return  always false
     * @throws  MessagingException
     * @throws  IOException
     */
    protected boolean attachHtmlParts(final Part part) throws MessagingException, IOException
    {
        return false;
    }
}
