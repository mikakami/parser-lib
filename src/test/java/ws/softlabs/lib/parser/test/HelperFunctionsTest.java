package ws.softlabs.lib.parser.test;

import java.util.List;

import org.junit.Test;

import ws.softlabs.lib.parser.server.KinovlruParser;

public class HelperFunctionsTest {
	
	
	@SuppressWarnings("unused")
	@Test
	public void testGetDaysList() {
		
		KinovlruParser parser = new KinovlruParser(null); 
		
		String range1 = "Воскресенье, 1 мая — Понедельник, 2 мая";
		String range2 = "Понедельник, 2 мая — Среда, 4 мая";
		String range3 = "Суббота, 30 апреля — Понедельник, 2 мая";
		String range4 = "Вторник, 3 мая";
		
		List<String> days = parser.getDaysList(range4);
		for(String s : days)
			System.out.println(s);
		
	}
}
