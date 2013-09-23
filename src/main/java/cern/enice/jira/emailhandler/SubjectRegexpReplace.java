package cern.enice.jira.emailhandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * 
 * @author Rafal Niesler rafal.niesler@cern.ch
 *
 */
public class SubjectRegexpReplace {
	private final Logger log = Logger
			.getLogger(SubjectRegexpReplace.class);
	private Pattern regexp;
	private String replace;
	private static final String GROUP_REGEXP="\\$(\\d+)";
	
	/**
	 * Creates empty object for later initialization. Afterwards, you must define at least regexp! 
	 */
	public SubjectRegexpReplace() {
		this.replace = "$0";
	}
	
	/**
	 * @param pattern Regexp to find patterns in message subject
	 * @param replace Replace pattern defining the look of output
	 */
	public SubjectRegexpReplace(String pattern, String replace) {
		this.regexp = Pattern.compile(pattern);
		this.replace = replace;
	}
	
	/**
	 * Creates object with default replace pattern: $0, thus rewriting as output the expression found by regexp pattern 
	 * @param pattern Regexp to find patterns in message subject
	 */
	public SubjectRegexpReplace(String pattern) {
		this.regexp = Pattern.compile(pattern);
		this.replace = "$0";	// 0 group means the whole expression
	}
	
	/**
	 * @return Regexp to find patterns in message subject
	 */
	public Pattern getRegexp() {
		return regexp;
	}

	/**
	 * @param regexp Regexp to find patterns in message subject
	 */
	public void setRegexp(String regexp) {
		this.regexp = Pattern.compile(regexp);
	}

	/**
	 * @return Replace pattern defining the look of output
	 */
	public String getReplace() {
		return replace;
	}

	/**
	 * @param replace Replace pattern defining the look of output
	 */
	public void setReplace(String replace) {
		this.replace = replace;
	}

	
	public Matcher getMatcher(String expr) {
		if(regexp==null)
			return null;
		return regexp.matcher(expr);
	}
//	/**
//	 * Equivalent of regexp.matcher(expr).matches()
//	 * @param expr String expression
//	 * @return regexp.matcher(expr).matches()
//	 */
//	public boolean matches(String expr) {
//		if(regexp==null)
//			return false;
//		Matcher matcher = regexp.matcher(expr);
//		return matcher.matches();
//	}
//	
//	/**
//	 * Equivalent of regexp.matcher(expr).find()
//	 * @param expr String expression
//	 * @return regexp.matcher(expr).find()
//	 */
//	public boolean find(String expr) {
//		if(regexp==null)
//			return false;
//		Matcher matcher = regexp.matcher(expr);
//		return matcher.find();
//	}
	
	/**
	 * Completes the pattern given as a replace pattern with groups found in a given expression (according to regexp pattern) and returns the final result.
	 * @param expr Expression to get information from
	 * @return Output defined as replace pattern
	 */
	public String getOutput(Matcher matcher) {
		// regexp must be defined!
		if(regexp==null || matcher==null)
			return null;
		String result = null;
//		if(matcher.find()) {
			// found expression
			result = replace;
			// find $\d+ in replace pattern
			Matcher groupmatcher = Pattern.compile(GROUP_REGEXP).matcher(replace);
			while(groupmatcher.find()) {
				// get group number
				Integer groupNo = new Integer(groupmatcher.group().substring(1));
				// replace group numbers for actual groups from expression found in subject
				if(groupNo==0)
					result=result.replaceAll("\\$"+groupNo, matcher.group());
				else
					result=result.replaceAll("\\$"+groupNo, matcher.group(groupNo));
				log.debug("Actual output result: "+result);
			}
//		}
		return result;
	}
	
}
