/*
 * Copyright (c) 2024-2025 Stefan Toengi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.schnippsche.solarreader.plugins.awattar;

import de.schnippsche.solarreader.backend.calculator.MapCalculator;
import de.schnippsche.solarreader.backend.connection.general.ConnectionFactory;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.backend.connection.network.HttpConnectionFactory;
import de.schnippsche.solarreader.backend.provider.AbstractHttpProvider;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.provider.ProviderProperty;
import de.schnippsche.solarreader.backend.singleton.GlobalUsrStore;
import de.schnippsche.solarreader.backend.table.*;
import de.schnippsche.solarreader.backend.util.JsonTools;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.backend.util.StringConverter;
import de.schnippsche.solarreader.database.Activity;
import de.schnippsche.solarreader.frontend.ui.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

/**
 * The {@code Awattar} class is an implementation of {@link AbstractHttpProvider}.
 *
 * <p>This class is designed to interact with the Awattar API, a service that provides electricity
 * price information based on the current market conditions. It uses HTTP connections to communicate
 * with the API and retrieve data. The default connection mechanism utilizes an instance of {@link
 * HttpConnectionFactory}, but a custom {@link ConnectionFactory} can be specified if needed.
 *
 * <p>The class also manages an internal {@code offset}, which is initialized to {@code
 * BigDecimal.ZERO} by default. This offset can be used for price adjustments or other calculations
 * based on the data retrieved from the API.
 */
public class Awattar extends AbstractHttpProvider {
  private static final String BASE_URL =
      "https://{provider_host}/v1/marketdata?start={epochMillis}";
  private static final String AWATTAR_PRICE = "awattar_price";
  private static final String TIMESTAMP_FORMAT =
      "DT_DATE_FORMAT(start_timestamp_hour_%d, \"dd.MM.yyyy HH:mm\")";
  private static final String PRICE_KEY_FORMAT = "marketprice_hour_%d";
  private static final String HOUR_FORMAT = "DT_DATE_FORMAT(start_timestamp_hour_%d, \"HH\")";
  private static final String TIMESTAMP_KEY_FORMAT = "start_timestamp_hour_%d";
  private BigDecimal offset;
  private Setting knownSetting;

  /**
   * Default constructor for the {@code Awattar} class.
   *
   * <p>This constructor creates a new instance of {@code Awattar} using a default implementation of
   * {@link HttpConnectionFactory}.
   */
  public Awattar() {
    this(new HttpConnectionFactory());
  }

  /**
   * Constructor for the {@code Awattar} class with a custom {@code ConnectionFactory}.
   *
   * <p>This constructor allows specifying a custom implementation of {@link ConnectionFactory} for
   * creating {@link HttpConnection} objects. The instance variable {@code offset} is initialized to
   * {@code BigDecimal.ZERO} by default.
   *
   * @param connectionFactory The custom implementation of {@link ConnectionFactory} to be used for
   *                          creating {@link HttpConnection} objects.
   */
  public Awattar(ConnectionFactory<HttpConnection> connectionFactory) {
    super(connectionFactory);
    this.offset = BigDecimal.ZERO;
    Logger.debug("instantiate {}", this.getClass().getName());
  }

  /**
   * Retrieves the resource bundle for the plugin based on the specified locale.
   *
   * <p>This method overrides the default implementation to return a {@link ResourceBundle} for the
   * plugin using the provided locale.
   *
   * @return The {@link ResourceBundle} for the plugin, localized according to the specified locale.
   */
  @Override
  public ResourceBundle getPluginResourceBundle() {
    return ResourceBundle.getBundle("awattar", locale);
  }

  @Override
  public Activity getDefaultActivity() {
    return new Activity(LocalTime.of(1, 0, 0), LocalTime.of(18, 0, 0), 1, TimeUnit.HOURS);
  }

  @Override
  public Optional<UIList> getProviderDialog() {
    UIList uiList = new UIList();
    uiList.addElement(
        new UITextElementBuilder().withLabel(resourceBundle.getString("awattar.title")).build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withId("id-awattar-price")
            .withRequired(true)
            .withType(HtmlInputType.NUMBER)
            .withStep("any")
            .withColumnWidth(HtmlWidth.HALF)
            .withLabel(resourceBundle.getString("awattar.price.text"))
            .withName(AWATTAR_PRICE)
            .withPlaceholder(resourceBundle.getString("awattar.price.text"))
            .withTooltip(resourceBundle.getString("awattar.price.tooltip"))
            .withInvalidFeedback(resourceBundle.getString("awattar.price.error"))
            .build());

    return Optional.of(uiList);
  }

  @Override
  public Optional<List<ProviderProperty>> getSupportedProperties() {
    return getSupportedPropertiesFromFile("awattar_fields.json");
  }

  @Override
  public Optional<List<Table>> getDefaultTables() {
    List<Table> tables = new ArrayList<>(1);
    Table table = new Table("awattarPreise");
    TableColumn dateColumn = new TableColumn("Datum", TableColumnType.STRING);
    table.addColumn(dateColumn);
    TableColumn priceColumn = new TableColumn("Preis_kWh", TableColumnType.NUMBER);
    table.addColumn(priceColumn);
    TableColumn hourColumn = new TableColumn("Stunde", TableColumnType.NUMBER);
    table.addColumn(hourColumn);
    TableColumn timestampColumn = new TableColumn("timestamp", TableColumnType.TIMESTAMP);
    table.addColumn(timestampColumn);
    for (int hour = 0; hour < 24; hour++) {
      TableRow tableRow = new TableRow();
      String preCondition = String.format(TIMESTAMP_KEY_FORMAT + " > 0", hour);
      tableRow.addCell(new TableCell(preCondition, String.format(TIMESTAMP_FORMAT, hour)));
      tableRow.addCell(new TableCell(preCondition, String.format(PRICE_KEY_FORMAT, hour)));
      tableRow.addCell(new TableCell(preCondition, String.format(HOUR_FORMAT, hour)));
      tableRow.addCell(new TableCell(preCondition, String.format(TIMESTAMP_KEY_FORMAT, hour)));
      table.addTableRow(tableRow);
    }
    tables.add(table);
    return Optional.of(tables);
  }

  @Override
  public Setting getDefaultProviderSetting() {
    Setting setting = new Setting();
    setting.setConfigurationValue(AWATTAR_PRICE, "0.00");
    setting.setProviderHost("api.awattar.de");
    setting.setReadTimeoutMilliseconds(5000);
    return setting;
  }

  @Override
  public String testProviderConnection(Setting testSetting)
      throws IOException, InterruptedException {
    HttpConnection connection = connectionFactory.createConnection(testSetting);
    URL testUrl = getApiUrl(testSetting, BASE_URL);
    connection.test(testUrl, HttpConnection.CONTENT_TYPE_JSON);
    return resourceBundle.getString("awattar.connection.successful");
  }

  @Override
  public void doOnFirstRun() {
    doStandardFirstRun();
  }

  @Override
  public boolean doActivityWork(Map<String, Object> variables)
      throws IOException, InterruptedException {
    variables.put("offset", offset);
    workProperties(getConnection(), variables);
    return true;
  }

  @Override
  public void configurationHasChanged() {
    super.configurationHasChanged();
    knownSetting = providerData.getSetting();
    offset = knownSetting.getConfigurationValueAsBigDecimal(AWATTAR_PRICE, BigDecimal.ZERO);
  }

  @Override
  public String getLockObject() {
    return knownSetting.getProviderHost();
  }

  private URL getApiUrl(Setting setting, String urlPattern) throws IOException {
    Map<String, String> configurationValues = setting.getConfigurationValues();
    ZonedDateTime localDateTime = GlobalUsrStore.getInstance().getCurrentZonedDateTime();
    long epochMillis = localDateTime.toInstant().toEpochMilli();
    configurationValues.put("epochMillis", String.valueOf(epochMillis));
    String urlString =
        new StringConverter(urlPattern).replaceNamedPlaceholders(configurationValues);
    Logger.debug("url:{}", urlString);
    return new StringConverter(urlString).toUrl();
  }

  @Override
  protected void handleCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty commandProviderProperty,
      Map<String, Object> variables)
      throws IOException, InterruptedException {
    String pattern = commandProviderProperty.getCommand();
    URL url = getApiUrl(knownSetting, pattern);
    Map<String, Object> values =
        new JsonTools().getSimpleMapFromJsonString(httpConnection.getAsString(url));
    new MapCalculator()
        .calculate(values, commandProviderProperty.getPropertyFieldList(), variables);
  }
}
