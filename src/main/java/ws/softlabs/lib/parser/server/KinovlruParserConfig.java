package ws.softlabs.lib.parser.server;

public class KinovlruParserConfig {
	private static int 			showColCount = 6;
	private static String		splitString  = "###";	
	private static int			connectionTimeout = 10000; // 10 seconds timeout
	
	public static final String	laquo     = "«";
	public static final String	raquo     = "»";
	public static final String	daySplit1 = " — ";
	
	/***** !!! DON'T CHANGE *****/
	private static String	baseURL      = "http://kino.vl.ru/theatres/"; 
	private static String	searchString = "theatres"; 

	public static int getShowColCount() {
		return showColCount;
	}
	public static void setShowColCount(int showColCount) {
		KinovlruParserConfig.showColCount = showColCount;
	}
	public static String getSplitString() {
		return splitString;
	}
	public static void setSplitString(String splitString) {
		KinovlruParserConfig.splitString = splitString;
	}
	public static int getConnectionTimeout() {
		return connectionTimeout;
	}
	public static void setConnectionTimeout(int ct) {
		connectionTimeout = ct;
	}
	public static String getBaseURL() {
		return baseURL;
	}
	public static void setBaseURL(String baseURL) {
		KinovlruParserConfig.baseURL = baseURL;
	}
	public static String getSearchString() {
		return searchString;
	}
	public static void setSearchString(String searchString) {
		KinovlruParserConfig.searchString = searchString;
	}	
	public static String getInputCharset() {
		return "windows-1251";
	}

}
