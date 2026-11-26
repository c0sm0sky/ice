# Recepty OpenRewrite

Faza `upload` recepty `pl.demo.ice.rewrite.MigrateServletWebXml` usuwa z każdego `web.xml`
serwlet klasy `com.icesoft.faces.component.inputfile.FileUploadServlet` oraz
jego mapowania. Identyfikuje serwlet po klasie, a mapowania po jego nazwie;
nie zakłada konkretnej nazwy, URL ani kolejności elementów. Inne serwlety
pozostają bez zmian. Recepta działa na XML przed receptami tekstowymi.

Już dostępne recepty wielokrotnego użytku obejmują m.in. migrację znaczników
ICEfaces, konfiguracji Web Flow, wyjątków Web Flow, Servletu i wykluczeń
WildFly. Plugin `IcefacesTagsToPrimeFaces` mapuje tagi ICEfaces, a dla wybranych
pól edycyjnych z `partialSubmit="true"` dodaje `p:ajax process="@this" update="@form"`,
instaluje skrypt zachowujący lokalnie zmienione wartości i dołącza go w
`mainTemplate.xhtml`. Rzeczywisty
zakres recept opisują klasy i testy; nie są kompletną migracją dowolnej aplikacji.

Skrypt przywraca wyłącznie pola z ID, które zgłosiły zdarzenie `input` lub
`change`. Nie przywraca listy, jeśli serwer zmienił jej opcje. Nie odtwarza
wyboru pliku, edytorów rich text ani niestandardowych widżetów; wymagają one
oddzielnej migracji i testów. Potwierdź zachowanie walidacji, obsługi zdarzeń
i kontenerów nazw w migrowanej aplikacji. Źródłem skryptu jest wyłącznie
`src/main/resources/primefaces-partial-submit.js` w pluginie. Recepta kopiuje zasób
do projektu PrimeFaces, zachowując istniejący plik docelowy, jeśli już jest.

Inwentaryzację wszystkich wystąpień `partialSubmit` wykonuje
`python3 rewrite-plugins/analyze_partial_submit.py src/main/webapp > partial-submit-report.json`.
Raport proponuje komponenty odwołujące się do tych samych beanów, w tym
tabele, wyjścia i pola. Są to kandydaci do sprawdzenia, a nie potwierdzone
zależności: beany wywoływane przez usługi i listenery mogą być niewidoczne,
a selektory ID i warunkowo renderowane komponenty wymagają dodatkowej analizy.
Narzędzie nie modyfikuje widoków ani nie ustawia `update` automatycznie.

Dalsi kandydaci do wydzielenia:

- XML-owe usuwanie pozostałych serwletów i listenerów ICEfaces po klasie,
  wraz z mapowaniami powiązanymi nazwą; najpierw ustalić reguły zachowania
  współdzielonych mapowań i pozostałej konfiguracji.
- Rozszerzenie mapowania `partialSubmit` na pozostałe komponenty AJAX PrimeFaces,
  z zachowaniem semantyki procesowania i wyjątków dla pobierania plików.
- Migracja zasobów ICEfaces do PrimeFaces na podstawie typów i powiązanych
  komponentów, po przygotowaniu reprezentatywnych przykładów i testów.

Reguły zależne od nazw beanów, flow, widoków, adresów, treści raportu oraz
`secure="false"` pozostają specyficzne dla projektu i nie powinny być
przedstawiane jako uniwersalne domyślne ustawienia.
