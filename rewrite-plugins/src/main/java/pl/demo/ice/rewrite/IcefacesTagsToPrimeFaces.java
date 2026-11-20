package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagAttributeKey;
import org.openrewrite.xml.ChangeTagName;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Buduje mapowania tagów na podstawie deskryptorów ICEfaces i PrimeFaces. */
public final class IcefacesTagsToPrimeFaces extends Recipe {
    private static final String ICEFACES_TAGLIB = "META-INF/icefaces_component.tld";
    private static final String PRIMEFACES_TAGLIB = "META-INF/primefaces.taglib.xml";
    private static final String ICEFACES_PREFIX = "ice";
    /** Komponenty bez rzetelnego zamiennika jeden do jednego; migracja podaje wskazówkę ręcznej przebudowy. */
    private static final Map<String, String> MANUAL_MIGRATIONS = Map.ofEntries(
            Map.entry("commandSortHeader", "przenieś nagłówek sortujący do atrybutów p:column sortBy/sortOrder"),
            Map.entry("dataPaginator", "ustaw paginator i rows bezpośrednio na p:dataTable"),
            Map.entry("gMapControl", "przenieś ustawienia kontrolek do atrybutów/modelu p:gmap"),
            Map.entry("gMapDirection", "zastąp kierunki osobnym modelem i API mapy używanym przez p:gmap"),
            Map.entry("gMapGeoXml", "przenieś warstwy GeoXML do modelu mapy lub własnej warstwy JavaScript"),
            Map.entry("gMapLatLng", "przenieś współrzędne do modelu/obiektu mapy PrimeFaces"),
            Map.entry("gMapLatLngs", "przenieś listę współrzędnych do modelu/obiektu mapy PrimeFaces"),
            Map.entry("gMapMarker", "przenieś znaczniki do modelu mapy PrimeFaces"),
            Map.entry("menuItems", "przebuduj dynamiczne menu jako model p:menu/p:submenu"),
            Map.entry("outputConnectionStatus", "zastąp własnym wskaźnikiem połączenia; p:poll nie jest równoważny"),
            Map.entry("outputDeclaration", "przenieś deklarację DOCTYPE poza drzewo komponentów XHTML"),
            Map.entry("outputResource", "zastąp p:fileDownload i dostarcz org.primefaces.model.StreamedContent"),
            Map.entry("panelBorder", "przebuduj regiony north/south/east/west/center w układzie CSS lub komponentach"),
            Map.entry("panelCollapsible", "użyj p:panel toggleable=true i przenieś stan/zdarzenia rozwijania"),
            Map.entry("panelLayout", "przebuduj pozycjonowanie w układzie CSS/Grid"),
            Map.entry("panelPositioned", "przenieś pozycjonowanie i iterację do XHTML/CSS oraz ui:repeat"),
            Map.entry("panelSeries", "przenieś generowanie powtarzanych komponentów do ui:repeat"),
            Map.entry("panelStack", "przebuduj przełączanie paneli; p:tabView ma inną semantykę"),
            Map.entry("portlet", "zastąp kontenerem p:panel lub własnym układem portletu"),
            Map.entry("rowSelector", "ustaw selectionMode i model zaznaczenia na p:dataTable/p:column"),
            Map.entry("setEventPhase", "przenieś obsługę do p:ajax i dobierz właściwy cykl przetwarzania JSF"),
            Map.entry("tabChangeListener", "obsłuż zmianę zakładki przez p:ajax event=tabChange"));
    private static final Map<String, String> JSF_AND_RENAMED_EQUIVALENTS = Map.ofEntries(
            Map.entry("form", "h:form"),
            Map.entry("inputHidden", "h:inputHidden"),
            Map.entry("inputSecret", "p:password"),
            Map.entry("inputFile", "p:fileUpload"),
            Map.entry("inputRichText", "p:textEditor"),
            Map.entry("loadBundle", "f:loadBundle"),
            Map.entry("menuBar", "p:menubar"),
            Map.entry("menuItem", "p:menuitem"),
            Map.entry("menuItemSeparator", "p:divider"),
            Map.entry("menuPopup", "p:contextMenu"),
            Map.entry("outputBody", "h:body"),
            Map.entry("outputChart", "p:chart"),
            Map.entry("outputFormat", "h:outputFormat"),
            Map.entry("outputHead", "h:head"),
            Map.entry("outputHtml", "html"),
            Map.entry("outputLink", "h:outputLink"),
            Map.entry("outputMedia", "p:media"),
            Map.entry("outputProgress", "p:progressBar"),
            Map.entry("outputStyle", "h:outputStylesheet"),
            Map.entry("outputText", "h:outputText"),
            Map.entry("panelDivider", "p:divider"),
            Map.entry("panelGroup", "h:panelGroup"),
            Map.entry("panelConfirmation", "p:confirmDialog"),
            Map.entry("panelPopup", "p:dialog"),
            Map.entry("panelTooltip", "p:tooltip"),
            Map.entry("panelTab", "p:tab"),
            Map.entry("panelTabSet", "p:tabView"),
            Map.entry("radio", "h:selectOneRadio"),
            Map.entry("selectInputDate", "p:datePicker"),
            Map.entry("selectInputText", "p:autoComplete"),
            Map.entry("selectManyListbox", "h:selectManyListbox"),
            Map.entry("gMap", "p:gmap"));

    @Override
    public String getDisplayName() {
        return "Migrate ICEfaces tags to PrimeFaces and Jakarta Faces";
    }

    @Override
    public String getDescription() {
        return "Derives same-name mappings from the ICEfaces and PrimeFaces tag libraries, applies known tag aliases, and stops on components requiring structural or Java-bean migration.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        Set<String> icefacesTags = readTagNames(ICEFACES_TAGLIB, "tag", "name");
        Set<String> primefacesTags = readTagNames(PRIMEFACES_TAGLIB, "tag", "tag-name");
        Map<String, String> mappings = new LinkedHashMap<>();
        icefacesTags.stream().filter(primefacesTags::contains)
                .forEach(tag -> mappings.put(tag, "p:" + tag));
        JSF_AND_RENAMED_EQUIVALENTS.forEach((oldName, newName) -> {
            if (!icefacesTags.contains(oldName)) {
                throw new IllegalStateException("ICEfaces tag is absent from its TLD: " + oldName);
            }
            String targetPrefix = prefix(newName);
            String targetTag = localName(newName);
            if ("p".equals(targetPrefix) && !primefacesTags.contains(targetTag)) {
                throw new IllegalStateException("PrimeFaces tag is absent from its tag library: " + targetTag);
            }
            mappings.put(oldName, newName);
        });
        Set<String> unmappedTags = new LinkedHashSet<>(icefacesTags);
        unmappedTags.removeAll(mappings.keySet());
        if (!unmappedTags.equals(MANUAL_MIGRATIONS.keySet())) {
            Set<String> unclassified = new LinkedHashSet<>(unmappedTags);
            unclassified.removeAll(MANUAL_MIGRATIONS.keySet());
            Set<String> stale = new LinkedHashSet<>(MANUAL_MIGRATIONS.keySet());
            stale.removeAll(unmappedTags);
            throw new IllegalStateException("Taglib ICEfaces nie ma pełnej klasyfikacji. Nowe: " + unclassified
                    + "; nieaktualne: " + stale);
        }

        List<Recipe> recipes = new java.util.ArrayList<>();
        recipes.add(new ChangeTagAttributeKey("//@xmlns:ice", "xmlns:p"));
        // Migracja Jakarta może wcześniej przepisać URI ICEfaces na wartość tymczasową "primefaces".
        // Ustawiamy docelowy URI bez warunku na poprzednią wartość.
        recipes.add(new ChangeTagAttribute("//*[local-name()='view']", "xmlns:p",
                "http://primefaces.org/ui", null, false));
        recipes.add(new ChangeTagAttribute("//*[local-name()='composition']", "xmlns:p",
                "http://primefaces.org/ui", null, false));
        recipes.add(new FailOnUnmappedIcefacesTags(mappings.keySet()));
        mappings.forEach((oldName, newName) -> recipes.add(
                new ChangeTagName("//" + ICEFACES_PREFIX + ":" + oldName, newName)));
        return recipes;
    }

    static Set<String> getIcefacesTagNames() {
        return readTagNames(ICEFACES_TAGLIB, "tag", "name");
    }

    static Set<String> getPrimefacesTagNames() {
        return readTagNames(PRIMEFACES_TAGLIB, "tag", "tag-name");
    }

    static Map<String, String> getManualMigrations() {
        return MANUAL_MIGRATIONS;
    }

    private static Set<String> readTagNames(String resource, String tagElement, String nameElement) {
        try (InputStream stream = IcefacesTagsToPrimeFaces.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing tag-library descriptor: " + resource);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(stream);
            var elements = document.getElementsByTagNameNS("*", tagElement);
            if (elements.getLength() == 0) {
                elements = document.getElementsByTagName(tagElement);
            }
            Set<String> names = new LinkedHashSet<>();
            for (int i = 0; i < elements.getLength(); i++) {
                var children = elements.item(i).getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    var child = children.item(j);
                    String localName = child.getLocalName() == null ? child.getNodeName() : child.getLocalName();
                    if (nameElement.equals(localName) && child.getTextContent() != null) {
                        names.add(child.getTextContent().trim());
                        break;
                    }
                }
            }
            if (names.isEmpty()) {
                throw new IllegalStateException("No tag names found in descriptor: " + resource);
            }
            return names;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read tag-library descriptor " + resource, exception);
        }
    }

    private static String prefix(String tagName) {
        int colon = tagName.indexOf(':');
        return colon < 0 ? "" : tagName.substring(0, colon);
    }

    private static String localName(String tagName) {
        int colon = tagName.indexOf(':');
        return colon < 0 ? tagName : tagName.substring(colon + 1);
    }

    /** Stops the migration before the ICEfaces dependency is removed if a view uses an unmapped component. */
    private static final class FailOnUnmappedIcefacesTags extends Recipe {
        private final Set<String> mappedNames;

        private FailOnUnmappedIcefacesTags(Set<String> mappedNames) {
            this.mappedNames = Set.copyOf(mappedNames);
        }

        @Override
        public String getDisplayName() {
            return "Require mappings for ICEfaces tags before removing ICEfaces";
        }

        @Override
        public String getDescription() {
            return "Fails fast when an XHTML view uses a component that needs a structural, model, or listener migration, and reports a component-specific hint.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    String tagName = tag.getName();
                    if (tagName.startsWith(ICEFACES_PREFIX + ":")) {
                        String localName = tagName.substring(ICEFACES_PREFIX.length() + 1);
                        if (!mappedNames.contains(localName)) {
                            String hint = MANUAL_MIGRATIONS.getOrDefault(localName,
                                    "dodaj jawne mapowanie do recepty przed migracją");
                            throw new IllegalStateException("Ręczna migracja <" + tagName + ">: " + hint
                                    + " (plik " + getCursor().firstEnclosing(Xml.Document.class).getSourcePath() + ")");
                        }
                    }
                    return super.visitTag(tag, ctx);
                }
            };
        }
    }
}
