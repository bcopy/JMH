package cern.enice.jira.emailhandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.oro.text.perl.Perl5Util;

import com.atlassian.jira.plugins.mail.handlers.AbstractCommentHandler;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.mail.MailUtils;

public class RegexCommentHandler extends AbstractCommentHandler
{
    private static final Logger log = Logger.getLogger(RegexCommentHandler.class);
    
    private static final String KEY_SPLITREGEX = "splitregex";
    private String splitRegex;

    public void init(Map params, MessageHandlerErrorCollector monitor)
    {
        super.init(params, monitor);
        if (params.containsKey(KEY_SPLITREGEX))
        {
            setSplitRegex((String) params.get(KEY_SPLITREGEX));
        }
    }

    protected String getEmailBody(Message message) throws MessagingException
    {
        return splitMailBody(MailUtils.getBody(message));
    }

    public String splitMailBody(String rawBody)
    {
        try
        {
            if (StringUtils.isNotEmpty(getSplitRegex()))
            {
                Perl5Util perl5Util = new Perl5Util();
                List parts = new ArrayList();
                perl5Util.split(parts, getSplitRegex(), rawBody);
                if (parts.size() > 1)
                {
                    StringBuffer comment = new StringBuffer("\n");
                    comment.append(((String) parts.get(0)).trim());
                    comment.append("\n\n");
                    return comment.toString();
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to split email body. Appending raw content...", e);
        }
        return rawBody;
    }

    public String getSplitRegex()
    {
        return splitRegex;
    }

    public void setSplitRegex(String splitRegex)
    {
        this.splitRegex = splitRegex;
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
