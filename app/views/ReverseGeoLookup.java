/* Copyright 2015 Fabian Steeg, hbz. Licensed under the GPLv2 */
package views;

import play.Logger;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.ws.WS;

/**
 * Reverse lookup of a given geolocation to get a label for it.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class ReverseGeoLookup {

	private String labelLookupURl;
	private String idLookupUrl;
	private int timeout;

	/**
	 * @param location A geolocation formatted as latitude,longitude, e.g.
	 *          "51.433333391323686,7.800000105053186"
	 * @return A label for the given location, e.g. "Menden (Sauerland)"
	 */
	public static String of(String location) {
		return new ReverseGeoLookup(
				"https://wdq.wmflabs.org/api?q=around[625,%s,0.1]",
				"https://www.wikidata.org/w/api.php?action=wbgetentities&props=labels&ids=Q%s&languages=de&format=json",
				10000).lookup(location);
	}

	private ReverseGeoLookup(String idLookupUrl, String labelLookupURl,
			int timeout) {
		this.idLookupUrl = idLookupUrl;
		this.labelLookupURl = labelLookupURl;
		this.timeout = timeout;
	}

	private String lookup(String location) {
		Object cached = Cache.get(location);
		if (cached != null) {
			Logger.debug("Using location label from cache for: " + location);
			return (String) cached;
		}
		//@formatter:off
		Promise<String> promise =
				WS.url(String.format(idLookupUrl,location)).get().flatMap(idResponse -> 
						WS.url(String.format(labelLookupURl,
								idResponse.asJson().get("items").elements().next().asInt()))
						.get().map(labelResponse -> 
								labelResponse.asJson().findValue("value").asText()))
				.recoverWith(throwable -> {
					throwable.printStackTrace();
					return Promise.pure(String.format("Unbekannter Ort (%s)", location));
				});
		//@formatter:on
		promise.onRedeem(label -> Cache.set(location, label));
		return promise.get(timeout);
	}
}
