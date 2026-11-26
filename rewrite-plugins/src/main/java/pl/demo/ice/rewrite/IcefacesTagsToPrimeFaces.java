package pl.demo.ice.rewrite;

import org.openrewrite.Recipe;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagAttributeKey;
import org.openrewrite.xml.ChangeTagName;
import org.openrewrite.xml.AddTagAttribute;
import org.openrewrite.text.CreateTextFile;
import org.openrewrite.text.FindAndReplace;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.Option;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Migrates ICEfaces tags and their partial-submit behavior to PrimeFaces. */
public final class IcefacesTagsToPrimeFaces extends Recipe {
    private static final String ICEFACES_TAGLIB = "META-INF/icefaces_component.tld";
    private static final String PRIMEFACES_TAGLIB = "META-INF/primefaces.taglib.xml";
    private static final String ICEFACES_PREFIX = "ice";
    private static final String PARTIAL_SUBMIT_SCRIPT_RESOURCE = "/primefaces-partial-submit.js";
    private static final String PARTIAL_SUBMIT_SCRIPT_PATH = "src/main/webapp/resources/js/primefaces-partial-submit.js";
    private static final String MAIN_TEMPLATE_PATH = "src/main/webapp/WEB-INF/layouts/mainTemplate.xhtml";
    private final boolean includePartialSubmitSupport;
    @Option(displayName = "Tags migrated by a later recipe",
            description = "Classified structural migrations handled by a later recipe in the same migration.",
            example = "outputResource")
    public Set<String> tagsMigratedLater = Set.of();

    public IcefacesTagsToPrimeFaces() {
        this(true);
    }

    IcefacesTagsToPrimeFaces(boolean includePartialSubmitSupport) {
        this.includePartialSubmitSupport = includePartialSubmitSupport;
    }

    public IcefacesTagsToPrimeFaces(Set<String> tagsMigratedLater) {
        this(tagsMigratedLater, true);
    }

    IcefacesTagsToPrimeFaces(Set<String> tagsMigratedLater, boolean includePartialSubmitSupport) {
        this(includePartialSubmitSupport);
        this.tagsMigratedLater = Set.copyOf(tagsMigratedLater);
    }
    /** Komponenty bez rzetelnego zamiennika jeden do jednego; migracja podaje wskazówkę ręcznej przebudowy. */
    private static final Map<String, String> MANUAL_MIGRATIONS = Map.ofEntries(
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
            Map.entry("commandSortHeader", "h:panelGroup"),
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
        return "Migrate ICEfaces tags and partial submits to PrimeFaces";
    }

    @Override
    public String getDescription() {
        return "Maps ICEfaces tags to PrimeFaces and Jakarta Faces, migrates partialSubmit behavior, installs its runtime script, and stops on components requiring structural or Java-bean migration.";
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
        if (!MANUAL_MIGRATIONS.keySet().containsAll(tagsMigratedLater)) {
            Set<String> unsupportedOverrides = new LinkedHashSet<>(tagsMigratedLater);
            unsupportedOverrides.removeAll(MANUAL_MIGRATIONS.keySet());
            throw new IllegalStateException("Nie można odroczyć nieznanych tagów ICEfaces: " + unsupportedOverrides);
        }

        List<Recipe> recipes = new java.util.ArrayList<>();
        recipes.add(new MigrateIcefacesTableSorting());
        Set<String> handledTags = new LinkedHashSet<>(mappings.keySet());
        handledTags.addAll(tagsMigratedLater);
        recipes.add(new FailOnUnmappedIcefacesTags(handledTags));
        mappings.forEach((oldName, newName) -> recipes.add(
                new ChangeTagName("//" + ICEFACES_PREFIX + ":" + oldName, newName)));
        // Przepisz elementy, zanim zmienisz deklarację xmlns:ice; w przeciwnym razie XPath ice:* traci prefiks.
        recipes.add(new ChangeTagAttributeKey("//@xmlns:ice", "xmlns:p"));
        // Migracja Jakarta może wcześniej przepisać URI ICEfaces na wartość tymczasową "primefaces".
        // Ustawiamy docelowy URI bez warunku na poprzednią wartość.
        recipes.add(new ChangeTagAttribute("//*[local-name()='view']", "xmlns:p",
                "http://primefaces.org/ui", null, false));
        recipes.add(new ChangeTagAttribute("//*[local-name()='composition']", "xmlns:p",
                "http://primefaces.org/ui", null, false));
        // ICEfaces command links submit through AJAX by default; preserve that behavior explicitly in PrimeFaces.
        recipes.add(new AddTagAttribute("//p:commandLink", "ajax", "true"));
        // ICEfaces menu items that invoke actions also migrate to AJAX PrimeFaces menu items.
        recipes.add(new AddTagAttribute("//p:menuitem", "ajax", "true"));
        // Zamień statyczne ścieżki JSF resources/{library}/{name} na atrybuty biblioteki Faces.
        recipes.add(new MigrateFacesResourceUrlsToLibrary());
        // Zachowaj etykiety panelTab po zmianie ich na p:tab.
        recipes.add(new ChangeTagAttributeKey("//p:tab/@label", "title"));
        if (includePartialSubmitSupport) {
            recipes.add(new PartialSubmitSupport());
        }
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

    /** Migrates partialSubmit on PrimeFaces components and installs its shared client runtime. */
    private static final class PartialSubmitSupport extends Recipe {
        private static final Set<String> EDITABLE = Set.of("selectOneMenu", "selectOneListbox",
                "selectManyListbox", "selectOneRadio", "selectManyCheckbox", "selectBooleanCheckbox",
                "inputText", "inputTextarea", "inputSecret", "password", "datePicker", "autoComplete");

        @Override
        public String getDisplayName() {
            return "Migrate ICEfaces partialSubmit behavior to PrimeFaces AJAX";
        }

        @Override
        public String getDescription() {
            return "Migrates partialSubmit to PrimeFaces AJAX, installs the shared input-preservation script, and includes it in the main template.";
        }

        @Override
        public List<Recipe> getRecipeList() {
            try (InputStream resource = IcefacesTagsToPrimeFaces.class
                    .getResourceAsStream(PARTIAL_SUBMIT_SCRIPT_RESOURCE)) {
                if (resource == null) {
                    throw new IllegalStateException("Missing recipe resource: " + PARTIAL_SUBMIT_SCRIPT_RESOURCE);
                }
                String script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                String templateReplacement = "<h:outputScript library=\"js\" name=\"primefaces-partial-submit.js\" target=\"head\" />\n            </h:head>";
                return List.of(
                        new CreateTextFile(script, PARTIAL_SUBMIT_SCRIPT_PATH, false),
                        new FindAndReplace("</h:head>", templateReplacement, null, null, null, null,
                                MAIN_TEMPLATE_PATH, null)
                );
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read recipe resource: " + PARTIAL_SUBMIT_SCRIPT_RESOURCE, e);
            }
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag updated = super.visitTag(tag, ctx);
                    if (!(updated.getName().startsWith("p:") || updated.getName().startsWith("h:"))
                            || !EDITABLE.contains(localName(updated.getName()))
                            || !"true".equals(attribute(updated, "partialSubmit"))) {
                        return updated;
                    }

                    updated = withoutAttribute(updated, "partialSubmit");
                    boolean alreadyHasAjax = updated.getChildren().stream()
                            .anyMatch(child -> "ajax".equals(localName(child.getName()))
                                    && child.getName().startsWith("p:"));
                    return alreadyHasAjax ? updated : appendChild(updated,
                            Xml.Tag.build("<p:ajax process=\"@this\" update=\"@form\" onstart=\"PrimeFacesPartialSubmit.capture(cfg.source)\" oncomplete=\"PrimeFacesPartialSubmit.restore()\" onerror=\"PrimeFacesPartialSubmit.clear()\" />"));
                }
            };
        }

        private static Xml.Tag withoutAttribute(Xml.Tag tag, String name) {
            List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes().size());
            boolean changed = false;
            for (Xml.Attribute attribute : tag.getAttributes()) {
                if (name.equals(localName(attribute.getKeyAsString()))) changed = true;
                else attributes.add(attribute);
            }
            return changed ? tag.withAttributes(attributes) : tag;
        }

        private static Xml.Tag appendChild(Xml.Tag parent, Xml.Tag child) {
            List<Content> content = parent.getContent() == null
                    ? new ArrayList<>() : new ArrayList<>(parent.getContent());
            if (content.isEmpty()) {
                String indent = parent.getPrefix().isBlank() ? "\n  " : parent.getPrefix();
                Xml.Tag.Closing closing = parent.getClosing();
                if (closing == null) {
                    closing = Xml.Tag.build("<holder></holder>").getClosing().withName(parent.getName());
                }
                content.add(child.withPrefix(indent + "  "));
                return parent.withContent(content).withClosing(closing.withPrefix(indent));
            }

            String indent = parent.getChildren().isEmpty()
                    ? "\n    " : parent.getChildren().getLast().getPrefix();
            int insertionPoint = content.size();
            if (content.get(insertionPoint - 1) instanceof Xml.CharData data && data.getText().isBlank()) {
                insertionPoint--;
            }
            content.add(insertionPoint, child.withPrefix(indent));
            return parent.withContent(content);
        }

        private static String attribute(Xml.Tag tag, String name) {
            return tag.getAttributes().stream()
                    .filter(attribute -> name.equals(localName(attribute.getKeyAsString())))
                    .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
        }
    }

    /** Converts JSF resource URLs into library/name references without assuming a particular library or file. */
    private static final class MigrateFacesResourceUrlsToLibrary extends Recipe {
        @Override
        public String getDisplayName() {
            return "Convert JSF resource URLs to library references";
        }

        @Override
        public String getDescription() {
            return "Converts h:outputStylesheet href values under resources/{library}/{name} to library and name attributes.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag updated = super.visitTag(tag, ctx);
                    if (!"h:outputStylesheet".equals(updated.getName())) return updated;
                    String href = updated.getAttributes().stream()
                            .filter(attribute -> "href".equals(attribute.getKeyAsString()))
                            .map(Xml.Attribute::getValueAsString)
                            .findFirst().orElse(null);
                    String[] resource = parseResourceUrl(href);
                    if (resource == null) return updated;
                    List<Xml.Attribute> attributes = new ArrayList<>(updated.getAttributes().size());
                    for (Xml.Attribute attribute : updated.getAttributes()) {
                        if (!"href".equals(attribute.getKeyAsString())) attributes.add(attribute);
                    }
                    updated = updated.withAttributes(attributes);
                    updated = setAttribute(updated, "name", resource[1]);
                    return setAttribute(updated, "library", resource[0]);
                }
            };
        }

        private static String[] parseResourceUrl(String href) {
            if (href == null || href.contains("?") || href.contains("#")
                    || href.contains("#{") || href.contains("${")) return null;
            String path = href;
            while (path.startsWith("../")) path = path.substring(3);
            while (path.startsWith("./")) path = path.substring(2);
            if (path.startsWith("/")) path = path.substring(1);
            if (!path.startsWith("resources/")) return null;
            String resourcePath = path.substring("resources/".length());
            int separator = resourcePath.indexOf('/');
            if (separator <= 0 || separator == resourcePath.length() - 1) return null;
            return new String[]{resourcePath.substring(0, separator), resourcePath.substring(separator + 1)};
        }

        private static Xml.Tag setAttribute(Xml.Tag tag, String name, String value) {
            List<Xml.Attribute> attributes = new ArrayList<>(tag.getAttributes());
            for (int i = 0; i < attributes.size(); i++) {
                if (name.equals(attributes.get(i).getKeyAsString())) {
                    attributes.set(i, attributes.get(i).withValue(attributes.get(i).getValue().withValue(value)));
                    return tag.withAttributes(attributes);
                }
            }
            String escaped = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
            Xml.Attribute added = Xml.Tag.build("<holder " + name + "=\"" + escaped + "\"/>")
                    .getAttributes().get(0).withPrefix(" ");
            attributes.add(added);
            return tag.withAttributes(attributes);
        }
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
