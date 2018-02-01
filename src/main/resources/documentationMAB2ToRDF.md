This table should answer questions like "where does this json comes from?". It builds partly on https://github.com/hbz/lobid/issues/161#issuecomment-272420198. → stands for "mapped on". Nearly identical titles were put together to shorten the table. Status: 31st January 2018

Number MAB2 | Field name MAB2 | if & how transformed
---------- | -------------- | ------------------------
001	| IDENTIFIKATIONSNUMMER DES DATENSATZES	| Contains subject URI / volume number
002	|	DATUM DER ERSTERFASSUNG / FREMDDATENUEBERNAHME | Mapped on dc/terms/created if matches format, used for ns-lobid-resource/about
003	|	DATUM DER LETZTEN KORREKTUR | → dc/terms/modified
004	|	ERSTELLUNGSDATUM DES AUSTAUSCHSATZES | -
005	|	TRANSAKTIONSDATUM | -
006	|	VERSIONSNUMMER | -
010	|	IDENTIFIKATIONSNUMMER DES DIREKT UEBERGEORDNETEN DATENSATZES | Used for isPartOfHbzId if matches with a lobid-resource.
011	|	IDENTIFIKATIONSNUMMER DER VERKNUEPFTEN SAETZE FUER PAUSCHALVERWEISUNGEN UND SIEHE-AUCH-HINWEISE | -
012	|	IDENTIFIKATIONSNUMMER DES TITELDATENSATZES (MAB-LOKAL) | -
015	|	IDENTIFIKATIONSNUMMER DES ZIELSATZES | -
016	|	IDENTIFIKATIONSNUMMER DES UMGELENKTEN SATZES | -
020	|	IDENTIFIKATIONSNUMMER EINES GELIEFERTEN DATENSATZES | -
021	|	IDENTIFIKATIONSNUMMER DER PRIMAERFORM | Combined with 619a and mapped to dc/terms/issued if latter matches a format.
022	|	IDENTIFIKATIONSNUMMER DER SEKUNDAERFORM | -
023	|	IDENTIFIKATIONSNUMMER DES ZU KORRIGIERENDEN SATZES | -
025	|	UEBERREGIONALE IDENTIFIKATIONSNUMMER | a → http://purl.org/lobid/lv#zdbId, o → bibo/oclcnum, z → dc/terms/isPartOf
026	|	REGIONALE IDENTIFIKATIONSNUMMER | -
027	|	LOKALE IDENTIFIKATIONSNUMMER | -
028	|	IDENTIFIKATIONSNUMMER VON NORMDATEN | -
029	|	SONSTIGE IDENTIFIKATIONSNUMMER DES VORLIEGENDEN DATENSATZES | -
030	|	CODIERTE ANGABEN ZUM DATENSATZ | -
031	|	ANGABEN ZUM REDAKTIONSSATZ | -
036	|	LAENDERCODE | -
037	|	SPRACHENCODE | Mapped on LoC-ISO-639-2
038	|	CODE FUER HERKUNFTSSPRACHE / SPRACHE DES ORIGINALS | -
039	|	ZEITCODE | -
040	|	NOTATION FUER NORMDATEN | -
041	|	NOTATIONSSPEZIFISCHE CODIERUNGEN | -
050.0	|	DATENTRAEGER [Druckschrift] | → bibo/Manuscript    
050.3 | DATENTRAEGER [Mikroform] | → RDAMediaType/1002
050.4 | DATENTRAEGER [Blindenschrifttraeger] | → purl.org/library/BrailleBook and RDAproductionMethod/1010
050.5 | DATENTRAEGER [Audiovisuelles Medium / Bildliche Darstellung] | 5.ad → purl.org/library/CassetteTape, 5.b and 5.c → bibo/AudioVisualDocument and RDACarrierType/1050 [Video carriers], 5.d → bibo/Image
050.6 | DATENTRAEGER [Audiovisuelles Medium / Bildliche Darstellung] | 6.aj → mo/Vinyl and bibo/AudioDocument
050.7 | DATENTRAEGER [Medienkombination] | → RDAproductionMethod/1010 [printing] and http://iflastandards.info/ns/isbd/terms/mediatype/T1008 [multiple media]
050.8 | DATENTRAEGER [Computerdatei]| → http://rdvocab.info/termList/RDACarrierType/1010
050.9 | DATENTRAEGER [Spiele]| → schema.org/Game
050.10 | DATENTRAEGER [Landkarten] | → bibo/Map. Used for bibo/book if other publication types don't fit.
050 | DATENTRAEGER [not used] | 5-6.a[abcefghijklmn][CD-DA (Compact Disc Digital Audio, Single Compact Disc)/ CD-Bildplatte/ Tonband/ Micro-Cassette (Diktier- oder Stenocassette)/ Digital Audio Tape (DAT-Cassette)/ Digital Compact Cassette (DCC-Cassette)/ Cartridge (8-Track Cartridge)/ Drahtton (Stahlband)/ Walze (Zylinder)/ Klavierrolle (Mechanisches Klavier)/ Filmtonspur/ Tonbildreihe], 5-6.uu/yy/zz [unbekannt/nicht spezifiziert/sonstige audiovisuelle Medien], 8 RDAMediaType/1003 (Computer), 11-13 [Anzahl der physischen Einheiten]
051	|	VEROEFFENTLICHUNGSSPEZIFISCHE ANGABEN ZU BEGRENZTEN WERKEN | Used for bibo/book if 050 fits and other publication types don't. Not used: 1-3.[acgijopqsvz]. 
051.0 | Erscheinungsform | [nt] → bibo/MultiVolumeBook, m → bibo/Book
051.1-3 | Veroeffentlichungsart und Inhalt | b → bibo/Bibliography, d → bibo/ReferenceSource, e → bibo/ReferenceSource, f → Festschrift, h → bibo/Biography, k → bibo/Proceedings, l → [ns-lobid-vocab]Legislation, m → mo/PublishedScore, , n → bibo/Standard, r → bibo/Report, s → lobid/Statistics, t → bibo/Article, u → bibo/Thesis, x → bibo/Schoolbook and bibo/Book
051.4 | Literaturtyp | [flks] → bibo/Book
051.6 | Kennzeichnung Amtlicher Druckschriften | → [ns-lobid-vocab]OfficialPublication
052	|	VEROEFFENTLICHUNGSSPEZIFISCHE ANGABEN ZU FORTLAUFENDEN SAMMELWERKEN | Used for bibo/book if 050 fits and other publication types don't. Not used: 1-6.ab/am/az/kt/da/di/es/ft/fz/fb/ha/in/li/lo/me/mg/pa/pt/re/rf/rg/so/st/ub/uu/xj. → bibframe/frequency.
052.1-6 | Veroeffentlichungsart und Inhalt | .aa → [ns-lobid-vocab]OfficialPublication, .ao → bibo/Newspaper, .ag → [ns-lobid-vocab]Legislation, .au → bibo/Article, .bi → [ns-lobid-vocab]Bibliography, .bg → [ns-lobid-vocab]Biography,  .eo → bibo/Newspaper, .ez  → bibo/ReferenceSource, .il → bibo/Periodical, .ko → bibo/Proceedings, .lp → bibo/Newspaper, .mu → .mo/PublishedScore, .no → bibo/Standard, .rp → bibo/Newspaper, sc → bibo/Thesis, .se → bibo/Series (if no ISBN from MAB 540), .up → bibo/Newspaper.
053	|	NACHLAESSE UND AUTOGRAPHEN | -
057	|	MATERIALSPEZIFISCHE CODES FUER MIKROFORMEN | → RDAMediaType/1002
058 | | → RDAMediaType/1003 (Computer)
062 | | → bibo/AudioDocument, bibo/AudioVisualDocument, http://rdaregistry.info/termList/RDAMediaType/1008 (Video)
065	|	NORMDATENSPEZIFISCHE ANGABEN ZUR PND | -
066	|	NORMDATENSPEZIFISCHE ANGABEN ZUR GKD | -
067	|	NORMDATENSPEZIFISCHE ANGABEN ZUR SWD | -
068	|	NORMDATENSPEZIFISCHE CODIERUNGEN | -
070	|	IDENTIFIZIERUNGSMERKMALE DER BEARBEITENDEN INSTITUTION | subfield a → schema.org/provider, subfield - → schema.org/sourceOrganization, subfield b → modifiedBy
071	|	IDENTIFIZIERUNGSMERKMALE DER BESITZENDEN INSTITUTION | -
072	|	CODIERTE ANGABEN ZUR BESITZENDEN INSTITUTION | -
073	|	SONDERSAMMELGEBIETSNUMMER | -
074	|	SONDERSAMMELGEBIETSNOTATION | -
075	|	ZDB-PRIORITAETSZAHL | -
076/079/081	|	FREI DEFINIERBARE ANWENDERSPEZIFISCHE ANGABEN, KENNZEICHEN UND CODES | -
078 | | → http://purl.org/lobid/lv#inCollection (e: e-Package, n: NWBib, r: edoweb, publisso)
080	|	ZUGRIFFS- UND UPDATE-ANWEISUNGEN | -
088	|	FREI DEFINIERBARE ANWENDERSPEZIFISCHE ANGABEN, KENNZEICHEN UND CODES | Used for creating item id. → bibframe/note, → bibframe/heldBy, → bibframe/itemOf, → bibframe/hasItem, → lobid/lv#callNumber
089	|	Bandangaben in Vorlageform | Used for [ns-lobid-vocab]numbering and creating title by concating superordinated title and volume number. One of the alternatives for non-existing 425.
090	|	Bandangaben in Sortierform | Used for [ns-lobid-vocab]numbering and creating title by concating superordinated title and volume number.
1?? |  | If something matches a GND-id in the 100s fields then it is put into creatorPersonId to work with it. Is put into marcrelName to test if it matches specific LoC vocabulary of bibo. 
100	|	Name der 1. Person in Ansetzungsform | Used for creatorLabel and then mapped on dc/terms/creator. subfield d: dateOfBirth, dateOfDeath, dateOfBirthAndDeath
101	|	Verweisungsformen zum Namen der 1. Person | 
102	|	Identifikationsnummer des Personennamensatzes der 1. Person | Used for creatorLabel and then mapped on dc/terms/creator.
103	|	Körperschaft, bei der die 1. Person beschäftigt ist | 
104	|	Name der 2. Person in Ansetzungsform | Used for creatorLabel and then mapped on dc/terms/creator. 104f mapped on id.loc.gov/vocabulary/relators/hnr if GND-id.
105	|	Verweisungsformen zum Namen der 2. Person | 
106	|	Identifikationsnummer des Personennamensatzes der 2. Person | 
107	|	Körperschaft, bei der die 2. Person beschäftigt ist | 
… 1[02468][048]	|	| Used for creatorLabel and then mapped on dc/terms/creator.
196	|	Name der 25. Person in Ansetzungsform | 
197	|	Verweisungsformen zum Namen der 25. Person | 
198	|	Identifikationsnummer des Personennamensatzes der 25. Person | 
199	|	Körperschaft, bei der die 25. Person beschäftigt ist |
2?? | | If something matches a GND-id in the 200s fields then it is put into creatorCorporateBodyId and contributorCorporateBodyId to work with it.
200	|	Name der 1. Körperschaft in Ansetzungsform |
201	|	Verweisungsformen zum Namen der 1. Körperschaft |
202	|	Identifikationsnummer des Körperschaftsnamensatzes der 1. Körperschaft |
204	|	Name der 2. Körperschaft in Ansetzungsform |
205	|	Verweisungsformen zum Namen der 2. Körperschaft |
206	|	Identifikationsnummer des Körperschaftsnamensatzes der 2. Körperschaft |
…	|	|
296	|	Name der 25. Körperschaft in Ansetzungsform |
297	|	Verweisungsformen zum Namen der 25. Körperschaft |
298 | Identifikationsnummer des Körperschaftsnamensatzes der 25. Körperschaft | 
300 | Sammlungsvermerk | -
304 | Einheitssachtitel | a → dc/terms/alternative
305	|	Identifikationsnummer des Einheitssachtitelsatzes | -
310 | Hauptsachtitel in Ansetzungsform | → Titel
331	|	Hauptsachtitel in Vorlageform oder Mischform | Used for creating title if no other title exists. Or for generating title by concating it with 089 or 090.
333 | zu ergänzende Urheber zum Hauptsachtitel | If no title exists, set as title. Also taken as CorporateBodyTitle.
334 | Allgemeine Materialbenennung | Match with Bibo/AudioDocument, bibo/AudioVisualDocument, bibo/Image, → RDAMediaType/1020 (Microform Carriers). Used for checking if full text is online.
335	|	Zusätze zum Hauptsachtitel | → rdvocab.info/Elements/otherTitleInformation.
340, 344 | Parallelsachtitel in Ansetzungsform | → dc/terms/alternative
341/345	|	[1./2.] Parallelsachtitel in Vorlageform oder Mischform | → dc/terms/alternative.
342, 346 | zu ergänzende Urheber zum Parallelsachtitel | → dc/terms/alternative
343/347	|	Zusätze zum [1./2.] Parallelsachtitel | → dc/terms/alternative.
348, 352 | Parallelsachtitel in Ansetzungsform | - 
349/353	|	[3./4.] Parallelsachtitel in Vorlageform oder Mischform | -
350, 354 | zu ergänzende Urheber zum Parallelsachtitel | -
351/355	|	Zusätze zum [3./4.] Parallelsachtitel | -
359	|	Verfasserangabe | → bibframe/responsibilityStatement
360	|	Unterreihen | → http://rdaregistry.info/Elements/u/P60517 (titleOfSubSeries)
361 | Beigefügte Werke | -
365	|	Zusätze zur gesamten Vorlage | -
369	|	Verfasserangabe zur gesamten Vorlage | -
370	|	Weitere Sachtitel | -
376	|	Normierte Zeitschriftentitel | → bibo/shortTitle.
400	|	Ausgabebezeichnung in normierter Form | subfield [an] → bibo/edition
403	|	Ausgabebezeichnung in Vorlageform | subfield [an] → bibo/edition
405	|	Erscheinungsverlauf | -
406	|	Normierter Erscheinungsverlauf | -
407	|	Kartographische Materialien: Mathematische Angaben | → rdvocab.info/Elements/longitudeAndLatitude.
411, 412, 416, 417, 418 | Alter Erscheinungsvermerk | -
410.a, 415.a | Ort des 1./2. Verlegers, Druckers usw. | → schema.org/publication
419.a | | → schema.org/publication, if 410.a and 415.a don't exist
420	|	Mehrteilige, unselbständig erschienene Werke: Zusammenfassende und offene Aufführung von Teilen | -
425	|	Erscheinungsjahr(e) | Used for creating dateFix or dateRange which is mapped on dc/terms/issued. → schema.org/startDate, → schema.org/endDate
426	|	Datumsangaben | -
427	|	Zusammenfassende Bestandsangaben | -
429	|	Bestandslücken | -
431	|	Zusammenfassende Register | -
432	|	Zusammenfassende und offene Bandaufführung | -
433	|	Umfangsangabe | → bibframe/extent
434	|	Illustrationsangabe / Technische Angaben zu Tonträgern | -
435	|	Formatangabe | -
437	|	Angabe von Begleitmaterialien | -
451/461/471/481/491	|	1./2./3./4./5. Gesamttitel in Vorlageform | → purl.org/lobid/lv#numbering, used for dc/elements/1.1/isPartOf.
452/462/472/482/492	|	Standardnummern des 1./2./3./4./5. Gesamttitels | -
453/463/473/483/493	|	Identifikationsnummer des 1./2./3./4./5. Gesamttitels | Used at idTitleSeries and then mapped on [ns-lobid-vocab]SeriesRelation, → purl.org/lobid/lv#series
454, 464, 474, 484, 494 | Gesamttitel in Ansetzungsform – wird auf Verbundebene entschieden! | -
455/465/475/485/495	|	Bandangabe [für 1./2./3./4./5. Gesamttitel] | -
456/466/476/486/496	|	Bandangabe in Sortierform [für 1./2./3./4./5. Gesamttitel] | -
501	|	Sammelfeld für unaufgegliederte Fußnoten | → skos/core#note.
502 | Einheitssachtitel eines beigefügten oder kommentierten Werkes | - 
503	|	Deutsche Übersetzung des Hauptsachtitels bzw. Hinweis auf die musikalische Form | -
504 | Angabe von Paralleltiteln | → dc/terms/alternative
505	|	Angabe von Nebentiteln | -
507	|	Angaben zum Hauptsachtitel und zu den Zusätzen | -
508	|	Angabe der Quelle der Aufnahme | -
509	|	Vermerke zur Verfasserangabe | -
510.a	|	Angaben zur Ausgabebezeichnung | → bibo/edition.
511	|	Angaben zum Erscheinungsvermerk | -
512	|	Angaben zum Kollationsvermerk bzw. zur physischen Beschreibung | -
513	|	Änderungen im Impressum | -
515	|	Ergänzungen zur Gesamttitelangabe | -
516	|	Angaben über Schrift, Sprache und Vollständigkeit der Vorlage und musikalische Notation | -
517 | Angaben zum Inhalt  | -
518	|	Angabe der Namen von Interpreten bzw. weitere Angaben zur Interpretation | -
519 | Alter Hochschulschriftenvermerk | If existing, multiple values are combined as RDA Elements/u/P60489 (has dissertation or thesis information)
522	|	Teilungsvermerk bei fortlaufenden Sammelwerken | -
523	|	Angaben über Erscheinungsweise und Erscheinungsdauer | → bibframe/note.
524	|	Hinweise auf unselbständig enthaltene Werke | -
525	|	Herkunftsangaben | Mapped on dc/terms/bibliographicCitation.
526	|	Titel von rezensierten Werken | -
527	|	Hinweise auf parallele Ausgaben | -
528	|	Titel von Rezensionen | -
529	|	Titel von fortlaufenden Beilagen | → bibframe/supplement
530	|	Titel von Bezugswerken | -
531	|	Hinweise auf frühere Ausgaben und Bände | → http://rdaregistry.info/Elements/u/P60261 (predecessor / Vorgänger)
532 | Hinweise auf frühere und spätere sowie zeitweise gültige Titel | -
533	|	Hinweise auf spätere Ausgaben und Bände | → http://rdaregistry.info/Elements/u/P60278 (successor / Nachfolger)
534	|	Titelkonkordanzen | -
535	|	Anzahl von Exemplaren | -
536	|	Voraussichtlicher Erscheinungstermin | -
537	|	Redaktionelle Bemerkungen | -
538	|	Angabe der Vervielfältigungsart | -
540	|	Internationale Standardbuchnummer (ISBN) | → bibo/isbn.
541	|	Internationale Standardnummer für Musikalien (ISMN) | → mo/ismn.
542	|	Internationale Standardnummer für fortlaufende Sammelwerke (ISSN) | → bibo/issn.
543	|	Internationale Standardnummer für Reports (ISRN) | -
544	|	Lokale Signatur | -
546	|	Postvertriebskennzeichen | -
550	|	Amtliche Druckschriftennummer | -
551	|	Verlags-, Produktions- und Bestellnummer von Musikalien und Tonträgern | -
552	|	Persistent Identifiers (PI) | If matches DOI or urn format mapped on bibo/doi or $[ns-lobid-vocab]urn respectively. subfield b → hasVersion (if no URN)
553	|	Artikelnummer | -
554	|	Hochschulschriftennummer | -
556	|	Reportnummer | -
562	|	Patentnummer | -
564	|	Normnummer | -
566	|	Firmenschriftennummer | -
568	|	Nationalbibliographienummer der CIP-Aufnahme | -
570	|	Nationalbibliographienummer der falschen Aufnahme | -
574	|	Nationalbibliographienummer (NBN) | -
576	|	Pflichtablieferungsnummer | -
578	|	Fingerprint | -
580	|	Sonstige Standardnummer | -
590	|	Hauptsachtitel und ggf. zu ergänzende Urheber der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
591	|	Verfasserangabe der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
592	|	Abteilung / Unterreihe der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
593	|	Ausgabebezeichnung der Quelle in Vorlageform | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
594.a	|	Erscheinungsort der Quelle | → schema.org/publication if 410.a, 415.a and 419.a don't exist.
595	|	Erscheinungsjahr der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation. Also used at @dateFix if no other value exists and then later mapped on dc/terms/issued. 
596	|	Bandzählung, Jahreszählung, Heftzählung, Umfangs- und Illustrationsangabe der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
597	|	Gesamttitel der Quelle | -
598	|	Fußnote der Quelle | Concat of 59[0123568] and mapped on dc/terms/bibliographicCitation.
599	|	Standardnummern der Quelle | Used at dctIsPartOfHbzIdArticle if matches a lobid-resource. Later used for dc/elements/1.1/isPartOf.
600/602	|	[1./2.] Teil der Verweisung | -
601/603	|	Bemerkungen zum [1./2.] Teil der Verweisung | -
610 – 645 | Segment Sekundärformen | 619a (Erscheinungsjahr(e) in Vorlageform) matched with 021 (Identifikationsnummer der Primaerform)
610.a | FUSSNOTE ZUR SEKUNDAERAUSGABE | → dc/terms/description
611.a | ORT(E) DES 1. VERLEGERS, HERSTELLERS USW. | → schema.org/location
613.a | NAME DES 1. VERLEGERS, HERSTELLERS USW. | → schema.org/publishedBy
619 | ERSCHEINUNGSJAHR(E) DER SEKUNDAERFORM | → schema.org/startDate, → schema.org/endDate
646	|	Besitznachweis für die Verfilmungsvorlage | -
647	|	Besitznachweis für den Sekundärform-Master | -
651	|	Fußnote zur Computerdatei | -
652 | Spezifische Materialbenennung und Dateityp | a (stands for RAK-NBM) → Online ressource, → RDAMediaType/1003 (Computer), → RDACarrierType/1018 (Online resource)
653 | Physische Beschreibung der Computerdatei auf Datenträger | -
654	|	Systemvoraussetzungen für die Computerdatei | -
655	|	Elektronische Adresse und Zugriffsart für eine Computerdatei im Fernzugriff | Used for checking if fulltext is online. Shown if no description is available. Used to define type → http://purl.org/lobid/lv#webPageArchived
659	|	Ergänzende Bemerkungen zur Computerdatei | -
661	|	Angaben zum Text der Unterlage | -
662	|	Angaben zum Äußeren der Unterlage | -
663	|	Bezugswerke | -
664	|	Provenienz | -
669	|	Redaktionelle Bemerkungen zur Unterlage | -
670	|	Sachtitel in abweichender Orthographie | -
671	|	Andersschriftliche Darstellung | -
672	|	Autorenname in normierter Form | -
673	|	Ort in normierter Form | -
674	|	Veranstaltungsjahr / Erscheinungsjahr des Originals | -
675	|	Stichwörter in abweichender Orthographie | → [ns-lobid-vocab]titleKeyword.
680	|	Werkverzeichnis | -
681	|	Angaben zum Musikwerk | -
682	|	Angaben zum Musikincipit | -
683	|	Angaben zur Besetzung | -
700	|	Notation eines Klassifikationssystems | → dc/terms/subject if matches dewey class. 700n → [ns-lobid-vocab]nwbibsubject. 700l → rpb-Notation and rpb-Raumsystematik
705	|	DDC (Dewey Decimal Classification) analytisch | → dc/terms/subject if matches dewey class.
710	|	Schlagwörter und Schlagwortketten | → purl.org/lobid/lv#subjectLabel.
711	|	Schlagwörter und Schlagwortketten nach anderen Regelwerken | → purl.org/lobid/lv#subjectLabel.
720	|	Stichwörter | -
730	|	Precis | -
740	|	Subject Headings | -
750	|	1. Inhaltliche Zusammenfassung | Mapped on dc/terms/abstract.
751/754/757	|	Verfasser der [1./2./3.] inhaltlichen Zusammenfassung | -
752/755/758	|	Sprachen der 1. inhaltlichen Zusammenfassung | -
753/756	|	[2./3.] inhaltliche Zusammenfassung | -
780 || → http://rdaregistry.info/Elements/u/P60261 (predecessor / Vorgänger)
785 || → http://rdaregistry.info/Elements/u/P60278 (successor / Nachfolger)
8XX | Segment Nichtstandardmäßige Nebeneintragungen | Matches with some GND-id?
9XX | Bei RSWK-Schlagwörtern erstes Unterfeld $f | Matches with some GND-id?
902/907/912/927/922/927/932/937/942/947 | → dc/terms/subject if subfield 9 exists.
