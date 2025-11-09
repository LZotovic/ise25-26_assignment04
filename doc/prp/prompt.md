Goal
    Das Feature soll einen neuen Point-of-Sale (POS) aus einem bestehenden OpenStreetMap-Eintrag importieren. Als Beispiel dient der OSM-Node 5589879349 („Rada Coffee & Rösterei“). 
Deliverable
    Ein REST-Endpoint, über den ein POS mithilfe einer OSM-Node-ID importiert werden kann.
    OSM-XML-Daten laden, relevanten Tags (name, addr: , opening_hours, website) sowie die Koordinaten (lat, lon) auslesen und daraus ein neues POS-Objekt erzeugen und speichern.
Success Definition
    Der Import gilt als erfolgreich, wenn eine valide OSM-Node-ID korrekt verarbeitet wird, die benötigten Informationen aus dem OSM-XML übernommen werden, ein neuer POS fehlerlos (ungültige ID oder Dubletten richtig behandeln) gespeichert wird.
User Persona
    Target User 
        Entwickler, der POS-Daten automatisiert aus OSM übernimmt.
Use Case
    Ein POS ohne manuelle Dateneingabe aus einem vorhandenen OSM-Node erzeugen.
User Journey
    1.	Anfrage mit einer OSM Node ID an den Endpunkt senden.
    2.	OSM-XML wird vom System geladen.
    3.	Relevante Daten werden extrahiert und in ein POS-Objekt überführt.
    4.	Neue POS speichern und als JSON zurückgeben.
Why
    Business value:
        Automatische Übernahme realer POS-Daten ohne manuelle Eingabe.
    Problems solved:
        Reduziert Arbeitsaufwand, Fehler und erhöht Effizienz bei der Erstellung neuer POS-Einträge.
What
    User-visible behavior:
        Der neue Endpunkt importiert einen POS auf Basis der NodeID und liefert die extrahierten Daten als JSON zurück. Fehlerhafte oder doppelte Einträge werden über passende Statuscodes signalisiert.
    Technical requirements:
        Verarbeitung der OSM-Node-ID
        Abruf und Parsing des OSM-XML
        POS-Speicherung und Fehlerbehandlung
