package ws.softlabs.lib.parser.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import ws.softlabs.lib.kino.dao.server.intf.DataService;
import ws.softlabs.lib.kino.model.client.Hall;
import ws.softlabs.lib.kino.model.client.Movie;
import ws.softlabs.lib.kino.model.client.Show;
import ws.softlabs.lib.kino.model.client.Theater;
import ws.softlabs.lib.util.client.Constants;
import ws.softlabs.lib.util.client.DateUtils;
import ws.softlabs.lib.util.client.DayComparator;
import ws.softlabs.lib.util.client.StringUtils;

public class KinovlruParser {

	private static final Logger log = 
		Logger.getLogger("kino.parser." + KinovlruParser.class.getSimpleName());
//	private static final Logger log2 = 
//		Logger.getLogger("kino.parser.detail." + KinovlruParser.class.getSimpleName());

	private DataService  	dataService;
	private Document 		currentDocument = null;
	private String			currentURL 		= null;

	public KinovlruParser(DataService dataService) {
		this.dataService = dataService;
		log.debug("CREATED");
	}
	/* get list of theaters */
	public Set<Theater> getTheaters()
	throws IOException {
		log.debug("ENTER");
		Set<Theater> result =
			getTheaters(KinovlruParserConfig.getBaseURL(),
						KinovlruParserConfig.getSearchString());
		log.debug("EXIT (result = " + result + ")");
		return result;
	}
	public Set<Theater> getTheaters(String url, String searchPath)
			throws IOException {
		log.debug("ENTER (url = " + url + ", searchPath = " + searchPath + ")");
		Set<Theater> theatres = new TreeSet<Theater>();
		Document doc = getDocument(url);
		Elements links = doc.select("a[href]");
		for (Element link : links) {
			String l = link.attr("abs:href");
			if (!link.attr("abs:href").endsWith("/"))
				l = link.attr("abs:href") + "/";
			if (l.contains(searchPath) && !l.endsWith(searchPath + "/")
					&& !link.text().isEmpty()) {
				Theater theater = dataService.getTheater(link.text().trim(), l);
				log.debug("got theater from dataService: " + theater);
				theatres.add(theater);
			}
		}
		log.debug("EXIT [set.size = " + theatres.size() + "]");
		return theatres;
	}
	/* get list of halls */
	public Set<Hall> getHalls(Theater theater) throws IOException {
		log.debug("ENTER (theater = " + theater + ")");
		Set<Hall> halls = new TreeSet<Hall>();
		Document doc = getDocument(theater.getUrl());
		Elements items = doc.select("h3");
		for (Element item : items)
			if (item.text().matches("^.{1,}\\s.*$")) {
				Hall hall = 
					dataService.getHall(theater, 
							item.text(),
							StringUtils.restoreHTMLString(item.text()));
				log.debug("got hall from dataService: " + hall);
				halls.add(hall);
			}
		if ( halls.size() == 0 ) {
			log.debug("adding default hall");
			Hall hall = dataService.getHall(theater, "", ""); 
			log.debug("got hall from dataService: " + hall);
			halls.add(hall);
		}
		log.debug("EXIT [set.size = " + halls.size() + "]");
		return halls;
	}
	/* get list of available schedules */
	public List<String> getTheaterShowDays(Theater theater) throws IOException {
		log.debug("ENTER (theater = " + theater + ")");
		Set<String> days = new TreeSet<String>();
		Elements items = getDocument(theater.getUrl()).select("h2");
		for (Element item : items) {
			if (item.text().matches("^.{1,},\\s\\d{1,2}\\s.*$")) {
				List<String> daysList = getDaysList(item.text());
				log.debug("adding list of days: [size = " + daysList.size() + "]");
				days.addAll(daysList);
			}
		}
		List<String> daysList = new ArrayList<String>(days);
		Collections.sort(daysList, new DayComparator());
		log.debug("EXIT [days.size = " + days.size() + "]");
		return daysList;
	}
	/*
	 * get show schedule for specific day (which should be read from getShowDays
	 * output)
	 */
	public Set<Show> getDayShows(String day, Hall hall)
			throws IOException {
		log.debug("ENTER (day = " + day + ", hall = " + hall + ")");
		Set<Show> shows = new TreeSet<Show>();

		/* create jsoup whitelist */
		Whitelist whitelist = new Whitelist();
		whitelist.addTags(	"html", "body", "a", "h2", "h3", "td", "tr", "table",
							"tbody", "th");
		whitelist.addAttributes("a", "href");
		/* create jsoup cleaner */
		Cleaner cleaner = new Cleaner(whitelist);
		/* get clean jsoup document */
		Document doc = cleaner.clean(getDocument(hall.getTheatre().getUrl()));

		/* crop garbage stuff */
		String s = doc.toString();

		/* get searchDay from all days on page and passed day */
		 String searchDay = getSearchDay(doc, day);
		log.debug("searchDay = " + searchDay);
		
		/* if we asked for the day, which is not present on web page */
		if (searchDay == null) {
			log.debug("EXIT (NULL) [searchDay = NULL]");
			return null;
		}
		int index = s.indexOf(searchDay);
		if (index == -1) {
			log.debug("EXIT (NULL) [index = -1 => searchDay not found on page]");
			return null;
		}
		s = s.substring(index);
		/* TODO: DOUBLE CHECK THIS IF STATEMENT !!! */
		if (!(hall.getName() == null) && s.indexOf(hall.getHtml()) == -1){
			log.debug("EXIT (NULL) [no hall found on page]");
			return null;
		}
		if (!(hall.getName() == null))
			s = s.substring(s.indexOf(hall.getHtml()));
		s = s.substring(s.indexOf("</tr>"));
		s = s.substring(s.indexOf("<tr>"));
		s = s.substring(s.indexOf("</tr>"));
		s = s.substring(s.indexOf("<tr>"));
		s = s.substring(s.indexOf("</tr>"));
		s = s.substring(s.indexOf("<tr>"));
		s = s.substring(0, s.indexOf("</tbody>"));
	
		/*
		 * make new tidy html document from cropped string with needed
		 * information
		 */
		doc = Jsoup.parse(s, hall.getTheatre().getUrl());

		/* get movie links */
		Elements movieLinks = doc.select("a");
		/* get table items */
		Elements items = doc.select("tr td");
		/* scan elements and fill result set */
		int counter = 0;
		String combinedString = "";
		for (Element item : items) {
			counter++;
			Element isMovie = checkIfItemIsMovie(movieLinks, item);
			if (isMovie != null) {
				combinedString += isMovie.attr("abs:href").trim()
						+ KinovlruParserConfig.getSplitString() + isMovie.text().trim()
						+ KinovlruParserConfig.getSplitString();
			} else
				combinedString += item.text().replaceAll("^.{1,}\\s{1,}$", "")
						+ KinovlruParserConfig.getSplitString();
			if (counter >= KinovlruParserConfig.getShowColCount()) {
				log.debug("combinedString = " + combinedString);
				log.debug("creating new Show");
				/**
				 * HERE CREATE NEW SHOW (OR GET EXISTING) AND ADD IT TO RESULT SET
				 */
				Movie   movie = getMovieFromString(combinedString, KinovlruParserConfig.getSplitString());
				Date date = getDateFromString(day, combinedString, KinovlruParserConfig.getSplitString());
				List<Integer> price = getPriceFromString(combinedString, KinovlruParserConfig.getSplitString());
				
				Show show = dataService.getShow(hall, movie, date, price); 
				log.debug("adding Show: " + show);
				shows.add(show);
				combinedString = "";
				counter = 0;
			}
		}
		log.debug("EXIT [shows.size = " + shows.size() + "]");
		return shows;
	}
	/* checks if HTML-element being parsed is movie
	 * (being a movie in this context means being in movieItems set) 
	 */
	private Element checkIfItemIsMovie(Elements movieItems, Element item) {
		log.debug("ENTER");
		for (Element movieItem : movieItems) {
			if (item.text().contains(movieItem.text())) {
				log.debug("EXIT (ELEMENT)");
				return movieItem;
			}
		}
		log.debug("EXIT (NULL)");
		return null;
	}
	/* get movie information from combined string */
	private Movie getMovieFromString(	String combinedString,
										String splitString ) {
		log.debug("ENTER (combinedString = " + combinedString +
					      ", splitString = " + splitString + ")");
		String[] strings = combinedString.split(splitString);
		/* TODO: if page changes recheck this ! */
		assert (strings.length == KinovlruParserConfig.getShowColCount()+1);
		String name = strings[2];
		String url  = strings[1];
		/*
		 *  if url is not null & name is null then this 
		 *  is not <a href..> element and we should 
		 *  treat url as name
		 */
		if (name == null && url != null) {
			String tmp = name;
			url = name;
			name = tmp;
		}
		Movie movie = dataService.getMovie(name, url);
		log.debug("EXIT [movie = " + movie + "]");
		return movie;
	}	
	/* get price list from combined string */
	private List<Integer> getPriceFromString(String combinedString,
											 String splitString ) {
		log.debug("ENTER (combinedString = " + combinedString +
			              ", splitString = " + splitString + ")");
		String[] strings = combinedString.split(splitString);
		assert (strings.length == KinovlruParserConfig.getShowColCount()+1);
		List<Integer> price = new ArrayList<Integer>();
		for (int i = 3; i < strings.length; i++) {
			List<String> prices = parsePriceString(strings[i]);
			for(String s : prices)
				try {
					price.add(Integer.parseInt(s));
				} catch(NumberFormatException nfex) {
					price.add(null);
				}			
		}		
		log.debug("EXIT [price.size = " + price.size() + "]");
		return price;
	}	
	/* get date-time from combined string and day string */
	@SuppressWarnings("deprecation")
	private Date getDateFromString( String day,
									String combinedString,
									String splitString ) {
		log.debug("ENTER (combinedString = " + combinedString +
  			              ", splitString = " + splitString + ")");
		String[] strings = combinedString.split(splitString);
		assert (strings.length == KinovlruParserConfig.getShowColCount()+1);
		Date showDate = DateUtils.stringToDate(day, strings[0]);
		log.debug("GOT DATE: " + showDate);
		/*  if showDate is after midnight but it's not morning yet
		 *  we should add one day to date */
		if (showDate.getHours() <= Constants.dayOffset) {
			showDate.setTime(showDate.getTime() + Constants.oneDay); // +1 day
			log.debug("FIXED DATE: " + showDate);
		}
		log.debug("EXIT [result = " + showDate + "]");
		return showDate;
	}
	/* gets a List of string (prices) from price string such as 123/321 */
	private List<String> parsePriceString(String string) {
		log.debug("ENTER (string = " + string + ")");
		List<String> prices = new ArrayList<String>();
		String[] s = string.split(" ");
		s[0].replaceAll(" ", "");
		if (!s[0].matches("^\\d{1,}/\\d{1,}$"))
			prices.add(s[0]);
		else {
			s = s[0].split("/");
			prices.add(s[0]);
			prices.add(s[1]);
		}
		log.debug("EXIT [prices.size = " + prices.size() + "]");
		return prices;
	}
	/* makes a List of days from string representing days-range */
	public List<String> getDaysList(String datesString) {
		log.debug("ENTER (datesString = " + datesString + ")");
		List<String> days = new ArrayList<String>();
		String[] s = datesString.split(StringUtils.fromUtf(KinovlruParserConfig.daySplit1));		
		if ((null != s) && (s.length > 1)) {
			String sBegin = s[0];
			String sEnd   = s[s.length-1];
			DateTime dBegin = new DateTime(DateUtils.stringToDate(sBegin));
			DateTime dEnd   = new DateTime(DateUtils.stringToDate(sEnd));
			for(DateTime dCurrent = dBegin; 
				dCurrent.isBefore(dEnd.toInstant()) || dCurrent.equals(dEnd); 
				dCurrent = dCurrent.plusDays(1)) {
					days.add(DateUtils.dateToStringSpecial(dCurrent.toDate()));
			}
			String newDay = DateUtils.dateToStringSpecial(dEnd.toDate());
			log.debug("adding day to list: " + newDay);
			days.add(newDay);
		} else {
			log.debug("adding single day: " + datesString);
			days.add(datesString);
		}
		log.debug("EXIT");
		return days;
	}
	/* returns search string ("date") which we should look for */
	public String getSearchDay(Document document, String day) {
		log.debug("ENTER (day = " + day + ")");
		String searchDay = null;
		Elements items = document.select("h2");
		for (Element item : items)
			if (item.text().matches("^.{1,},\\s\\d{1,2}\\s.*$")) {
				if (item.text().contains(day)) {
					log.debug("EXIT (contain day: " + day + ")");
					return day;
				}
				searchDay = checkDayRange(item.text(), day);
				if (searchDay != null) {
					log.debug("EXIT (found day: " + searchDay + ")");
					return searchDay;
				}
			}
		log.debug("EXIT (NULL)");
		return null;
	}
	/* checks if passed "day" string belongs the range "range" */
	public String checkDayRange(String range, String day) {
		log.debug("ENTER (range = " + range + ", day = "+ day + ")");
		String[] s = range.split(StringUtils.fromUtf(KinovlruParserConfig.daySplit1));
		if (null != s) {
			if (s.length > 1) {
				DateTime dBegin = new DateTime(DateUtils.stringToDate(s[0]));
				DateTime dEnd   = new DateTime(DateUtils.stringToDate(s[s.length-1]));
				DateTime dCheck = new DateTime(DateUtils.stringToDate(day));	
				if (
						(
							dCheck.isEqual(dBegin.toInstant())  ||
							dCheck.isAfter(dBegin.toInstant())
						) &&
						(
							dCheck.isEqual(dEnd.toInstant())   ||
							dCheck.isBefore(dEnd.toInstant())
						)
				) 
				log.debug("EXIT (s[0] - " + s[0] + ")");
				return s[0];
			} else { //s.length <= 1
				if (range.contains(day)) {
					log.debug("EXIT (day - " + day + ")");
					return day;
				}
			}
			
		}
		log.debug("EXIT (NULL)");
		return null;
	}
	private Document getDocument(String url) throws IOException  {
		log.debug("ENTER");
		if ( currentDocument == null || !url.equals(currentURL)) {
			log.debug("fetching new document");
			currentDocument =  Jsoup.connect(url)
			 							.timeout(KinovlruParserConfig
			 										.getConnectionTimeout())
			 							.get()
			 							;
			 currentURL      =  new String(url);
		} else {
			log.debug("returning old document");
		}
		log.debug("EXIT");
		return currentDocument;
	}
}
