package pl.demo.ice.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class MigrateIcefacesTableSortingTest implements RewriteTest {
    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateIcefacesTableSorting());
    }

    @Test
    void movesSortHeaderPropertyToColumnAndPreservesHeaderContent() {
        rewriteRun(xml("""
          <ice:dataTable var="row" sortColumn="login" sortAscending="true">
            <ice:column>
              <f:facet name="header">
                <ice:commandSortHeader columnName="login" arrow="true">
                  <ice:outputText value="Login"/>
                </ice:commandSortHeader>
              </f:facet>
              <ice:outputText value="#{row.login}"/>
            </ice:column>
          </ice:dataTable>
          """, """
          <ice:dataTable var="row">
            <ice:column sortBy="#{row.login}">
              <f:facet name="header">
                <ice:outputText value="Login"/>
              </f:facet>
              <ice:outputText value="#{row.login}"/>
            </ice:column>
          </ice:dataTable>
          """));
    }

    @Test
    void leavesTablesWithoutLegacySortHeadersAlone() {
        rewriteRun(xml("""
          <ice:dataTable var="row">
            <ice:column><ice:outputText value="#{row.login}"/></ice:column>
          </ice:dataTable>
          """));
    }

    @Test
    void derivesCacheFieldAndInvalidatorsFromBindings() {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.none()),
                xml("""
                  <beans>
                    <bean id="orderBean" class="example.OrderBean"/>
                    <p:dataTable value="#{orderBean.filteredOrders}">
                      <p:inputText value="#{orderBean.orderFilter}"/>
                      <p:commandButton action="#{orderBean.applyFilter}"/>
                    </p:dataTable>
                  </beans>
                  """),
                java("""
          import java.util.List;

          class OrderBean {
              private String orderFilter;
              public void setOrderFilter(String value) { this.orderFilter = value; }
              public List<String> getFilteredOrders() {
                  List<String> results = List.of(orderFilter);
                  return results;
              }
              public String applyFilter() { return null; }
          }
          """, """
          import java.util.List;

          class OrderBean {
              // Cache stabilnej listy tabeli dodany podczas migracji ICEfaces -> PrimeFaces.
              private List<String> filteredOrdersCache;
              private String orderFilter;
              public void setOrderFilter(String value) {
                  // Unieważnia cache po zmianie filtrów lub danych tabeli.
                  filteredOrdersCache = null; this.orderFilter = value; }
              public List<String> getFilteredOrders() {
                  List<String> results = List.of(orderFilter);
                  // Zachowuje tę samą listę, aby sortowanie PrimeFaces pozostało w modelu.
                  return filteredOrdersCache == null ? (filteredOrdersCache = results) : filteredOrdersCache;
              }
              public String applyFilter() {
                  // Unieważnia cache po zmianie filtrów lub danych tabeli.
                  filteredOrdersCache = null; return null; }
          }
          """));
    }
}
