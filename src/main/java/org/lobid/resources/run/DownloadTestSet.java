package org.lobid.resources.run;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gdata.util.common.base.Pair;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

/**
 * Download a MAB-XML test set via API calls, store as XML files.
 * 
 * IDs of the resources to download are given in a text file.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class DownloadTestSet {

	private static final String IDS = "src/test/resources/testIds.txt";
	private static final String API = "http://test.lobid.org/hbz01/%s";
	private static final String OUT = "src/test/resources/xml";
	private static final String XML = "text/xml";

	/**
	 * @param args 1. Path to IDs file, 2. API call format, 3. output directory.
	 *          Optional: Uses constants in this file if no args are passed.
	 */
	public static void main(String... args) {
		String ids = args.length > 0 ? args[0] : IDS;
		String api = args.length > 1 ? args[1] : API;
		String out = args.length > 2 ? args[2] : OUT;
		new File(out).mkdir();
		download(ids, api, out);
	}

	private static void download(String ids, String api, String out) {
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			Stream<String> hbzIds = Files.readAllLines(Paths.get(ids)).stream();
			hbzIds//
					.filter(id -> !outputFile(out, id).exists())//
					.map(toApiResponse(client, api))//
					.filter(successAndXml())//
					.forEach(writeToFileIn(out));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static File outputFile(String out, String id) {
		return new File(out, String.format("%s.xml", id));
	}

	private static Function<String, Pair<String, Response>> toApiResponse(
			AsyncHttpClient client, String api) {
		return hbzId -> {
			try {
				Response response = client.prepareGet(String.format(api, hbzId.trim()))
						.setHeader("Accept", XML).execute().get();
				return Pair.of(hbzId, response);
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		};
	}

	private static Predicate<Pair<String, Response>> successAndXml() {
		return idAndResponse -> idAndResponse != null
				&& idAndResponse.second.getStatusCode() == 200
				&& idAndResponse.second.getHeader("Content-Type").contains(XML);
	}

	private static Consumer<Pair<String, Response>> writeToFileIn(String out) {
		return idAndResponse -> {
			try {
				String responseBody =
						new String(idAndResponse.second.getResponseBodyAsBytes(),
								StandardCharsets.UTF_8);
				Path path = Paths.get(outputFile(out, idAndResponse.first).getPath());
				Files.write(path, Arrays.asList(responseBody), StandardCharsets.UTF_8);
				System.out.println("Wrote to " + path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
	}

}
