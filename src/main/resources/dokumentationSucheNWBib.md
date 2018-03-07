# Welche Suchen greifen auf welches MAB-/JSON-Feld zu? 
Stand: 15.02.2018
web/app/controllers/resources/Application.java
test.nwbib.de
Zur Erklärung: Alles was `markiert` ist, findet sich so in der MAB- bzw. JSON-Datei oder kann in die Suchmaske eingegeben werden. Mit der *einfachen Suche* ist der Suchschlitz gemeint, der auf der Startseite und zusammen mit der Trefferliste erscheint.
Alles, was in eckigen Klammern steht, ist eine Option. Z.B. wird mit `540[-ab][-1].[ab]` nach den MAB-Feldern und Unterfeldern mit den ersten Indikatoren "leer", "1" oder "2", dem zweiten Indikator "leer" oder "1" und den Unterfeldern "a" und "b" gesucht. Bei nummerierten Listen wird der erste zutreffende Eintrag genommen.
 Bei den Einträgen zum MAB-Feld wird kurz beschrieben, wie diese in das JSON-Feld umgewandelt werden. Punkte innerhalb der JSON- oder MAB-Notationen zeigen eine tiefere Ebene an, z.B. zeigt `publication.startDate` an, dass die Eigenschaft `startDate` in `publication` enthalten ist.
Als Beispiele werden die JSON-Dateien und MAB-XML-Dateien angegeben. 

## Erweiterte Suche
* Alle Wörter: Suche über alle Felder?
	* nach hbzId suchen: `HT019248992` 
* ISBN/ISSN
	* Erweiterte Suche: `9783892103929` bzw. `3892103925` oder `04506413`
	* Einfache Suche: `isbn:9783892103929` bzw. `isbn:3892103925` oder `issn:04506413`
	* JSON-Feld: `"isbn" : [ "3892103925", "9783892103929" ],` bzw. `"issn" : [ "04506413" ],`
	* MAB-Feld: `540[-ab][-1].[ab]` (für ISBN) bzw. `542[-ab][-1].a` (für ISSN). Bei ISBNs werden die Bindestriche weggenommen.
	* Beispiel ISBN: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)
		Alle ISBNs ohne Bindestriche suchen.
	* Beispiel ISSN: [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)
* Titel
	* Erweiterte Suche: `"Kölner Domblatt"`. Die Anführungsstriche werden für die Phrasensuche gesetzt.
	* Einfache Suche: `title:"Kölner Domblatt"`
	* JSON-Feld: `"title" : "Kölner Domblatt",`
	* MAB-Feld
		1. Zusammengesetzter Titel aus dem Titel der Überordnung (`331[-ab]2.a`), Bandangabe (`090-[-1].a` oder wenn nicht vorhanden, `089-[-1].a`) und dem Titel des Werks (`331[-ab][-1].a`).
		2. `310[-ab][-12].a`
		3. `331[-ab][-1].a`
		4. `333[-ab][-1].a`
	* Beispiel: [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)
* Person
	* Erweiterte Suche: `Brunert, Maria-Elisabeth`
	* Einfache Suche: `contribution.agent.label:"Brunert, Maria-Elisabeth"`
	* JSON-Feld: `contribution.agent.label`
	```
	"contribution" : [ {
	    "agent" : {
	      "dateOfBirth" : "1956",
	      "id" : "http://d-nb.info/gnd/11390472X",
	      "label" : "Brunert, Maria-Elisabeth",
	      "type" : [ "Person" ]
	    },
	    "role" : {
	      "id" : "http://id.loc.gov/vocabulary/relators/cre",
	      "label" : "Autor/in"
	    },
	    "type" : [ "Contribution" ]
	  } ],
	```
	* MAB-Feld: Jedes vierte 100er-Feld mit `[-abcefmn][12].[pa]`.
	* Beispiel: [HT019248992 (JSON)](http://lobid.org/resources/HT019248992.json) / [HT019248992 (MAB-XML)](http://lobid.org/hbz01/HT019248992) / [HT019248992 (NWBib)](https://nwbib.de/HT019248992)
* Körperschaft
	* Erweiterte Suche: `Zentral-Dombauverein`
	* Einfache Suche: `contribution.agent.label:Zentral-Dombauverein`
	* JSON-Feld: `contribution.agent.label`
	```
	"contribution" : [ {
	    "agent" : {
	      "altLabel" : [ "Dombauverein", "Central-Dombau-Verein" ],
	      "id" : "http://d-nb.info/gnd/42491-2",
	      "label" : "Zentral-Dombauverein (Köln)",
	      "type" : [ "CorporateBody" ]
	    },
	    "role" : {
	      "id" : "http://id.loc.gov/vocabulary/relators/ctb",
	      "label" : "Mitwirkende"
	    },
	    "type" : [ "Contribution" ]
	  } ],
	```
	* MAB-Felder 
		* Konferenzen und Ereignisse: Zusammengesetzt aus Ereignis (200er-Feld mit `[-abcfep][12].e`) und wenn vorhanden, Zählung (Unterfeld `n`), Datum (Unterfeld `d`) und Körperschaftsschlagwort (Ansetzung unter dem Ortssitz; Unterfeld `c`).
		* Erschaffende Körperschaften: Zusammengesetzt aus Körperschaft (ein 200er-Feld mit `[-a][12].k`) und wenn vorhanden mit Unterfeld `h`, `b` oder beidem.
		* Beitragende Körperschaften: Wie bei den erschaffenden Körperschaften nur mit dem ersten Indikator `b`, `c`, `e`, `f` oder `p`.
		* Verbindungen von geografischen Einheiten bzw. Körperschaften mit hierarchischer Information. Z.B. in BT000002852.
	* Beispiel: [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)
* Schlagwort
	* Erweiterte Suche: `Westfälischer Friede`
	* Einfache Suche: `subject.label:"Westfälischer Friede"`
	* JSON-Feld: `subject.label` und `subjectAltLabel`
	* MAB-Felder
		* Formalschlagwörter: `9[01234][27]-[-12].f`. Z.B. in HT002215064 ("Zeitschrift").
		* Sachschlagwörter: `9[01234][27]-[-12].s`. Z.B. in BT000004645 ("Führer").
		* Personen, Körperschaften, Ereignisse, Konferenzen, Orte wird ähnlich wie im Abschnitt "Person" bzw. "Körperschaft" erstellt.
		* Werke: Besteht `9[01234][27]-[-12].t` wird es teilweise mit dem Autoren verbunden. Z.B. in HT017034736 ("Viebig, Clara: Das Kreuz im Venn").
		* Freie Schlagwörter: `710[-abcdfz][123].a`. Z.B. in HT006934472 ("Düsseldorf").
		* Dewey-Notationen: `700[-b][-12345].a`, wenn es in 700 keinen Hinweis auf die DNB gibt (nur DDC-ähnliche Notationen) oder `705` mit Unterfeld `c`.
		* RSWK-Schlagwortketten: 
		* NWBib-Schlagwörter nach Raumsystematik: Siehe Regionensuche.
		* NWBib-Schlagwörter nach Sachsystematik: Siehe Sachgebietssuche.
		* `subjectAltLabel`: MAB 9[56][27] sind Verweisungsformen auf die Schlagwörter aus MAB 902-947.
			1. Wenn `9[56][27]-[12].[acefgkps]` vorhanden ist, verbinde es mit `9[56][27]-[12].b` oder `9[56][27]-[12].x` oder `9[56][27]-[12].[cdhmortuz]`.
			2. `9[56][27]-[12].[acefgkps]`
	* Beispiel für NWBib-Raumsystematik und RSWK-Schlagwort: [HT019248992 (JSON)](http://lobid.org/resources/HT019248992.json) / [HT019248992 (MAB-XML)](http://lobid.org/hbz01/HT019248992) / [HT019248992 (NWBib)](https://nwbib.de/HT019248992)
* Verlag
	* Erweiterte Suche: `Hayit`
	* Einfache Suche: `publication.publishedBy:Hayit`
	* JSON-Feld: `publication.publishedBy`
	```
	"publication" : [ {
	    "location" : "Köln",
	    "publishedBy" : "Hayit",
	    "startDate" : "1993",
	    "type" : [ "PublicationEvent" ]
	  } ],
	```
	* MAB-Feld: `41[27][-abcu][-12].[ag]` oder `419-[12].b`. Eckige Klammern und Zeichenketten, die nur `S.n.` bzw. `s.n.` enthalten, werden nicht berücksichtigt.
	* Beispiel: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)
* Erscheinungsjahr
	* Erweiterte Suche: `1842` oder `1998` 
	* Einfache Suche: `publication.startDate:1842`
	* JSON-Feld: `publication.startDate`, siehe Abschnitt "Verlag"
	* MAB-Feld: `425[ab-p][-1].a` oder `419-1c` oder `595-[-12].a`, wenn die Jahreszahl 1000-2099 entspricht.
	* Beispiel: [HT002215064 (JSON)](https://test.nwbib.de/HT002215064) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)
	* Anmerkung: Die Suche nach Erscheinungsjahren findet sowohl Erstpublikationen als auch Digitalisierungen bzw. andere Sekundärpublikationen. 

## Themensuche
* Themensuche: `Reise-, Stadt- und Wanderführer`
* Einfache Suche: `subject.label:"Reise-, Stadt- und Wanderführer"`
* JSON-Felder
	```
	"subject" : [ {
		"id" : "http://purl.org/lobid/nwbib#s102070",
		"label" : "Reise-, Stadt- und Wanderführer",
		"source" : {
			"id" : "http://purl.org/lobid/nwbib",
			"label" : "Sachsystematik der Nordrhein-Westfälischen Bibliographie"
		}
	},
	```
	Bei freien Schlagwörtern wird keine id angegeben.
* MAB-Felder
* Beispiel: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)

## Regionen
* Regionensuche über Drop-Down-Menü: `Westfalen`
* Einfache Suche: `subject.id:"http://purl.org/lobid/nwbib-spatial#n05"`
* JSON-Felder: `subject.id`, wenn es eine Raumsystematik-URL enthält.
	```
	"subject" : [ {
		"id" : "http://purl.org/lobid/nwbib-spatial#n05",
		"label" : "Westfalen",
		"source" : {
			"id" : "http://purl.org/lobid/nwbib-spatial",
			"label" : "Raumsystematik der Nordrhein-Westfälischen Bibliographie"
		}
	},
	```
* MAB-Felder: `700n[-1].a`. Wenn der Inhalt 00-89, 91, 96 oder 97 enthält, wird daraus eine URL erstellt.
* Beispiel: [HT019248992 (JSON)](http://lobid.org/resources/HT019248992.json) / [HT019248992 (MAB-XML)](http://lobid.org/hbz01/HT019248992) / [HT019248992 (NWBib)](https://nwbib.de/HT019248992)

## Sachgebiete
* Sachgebietssuche über Drop-Down-Menü: `102070 Reise-, Stadt- und Wanderführer`
* Einfache Suche: 
* JSON-Felder
	```
	"subject" : [ {
		"id" : "http://purl.org/lobid/nwbib#s102070",
		"label" : "Reise-, Stadt- und Wanderführer",
		"source" : {
			"id" : "http://purl.org/lobid/nwbib",
			"label" : "Sachsystematik der Nordrhein-Westfälischen Bibliographie"
		}
	},
	```
* MAB-Felder: `700n[-1].a`, wenn es eine sechsstellige Zahl enthält.
* Beispiel: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)

## Nur in einfacher Suche
* Suche nach hbzId
* Suche nach Enddatum
