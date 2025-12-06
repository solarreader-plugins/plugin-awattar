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

import static de.solarreader.core.connection.host.HostConnection.CONTENT_TYPE_JSON;

import de.solarreader.core.EnvironmentProfile;
import de.solarreader.core.Result;
import de.solarreader.core.config.Configuration;
import de.solarreader.core.config.HostConfig;
import de.solarreader.core.connection.general.ConnectionFactory;
import de.solarreader.core.connection.host.HostConnection;
import de.solarreader.core.connection.host.HostConnectionFactory;
import de.solarreader.core.field.Field;
import de.solarreader.core.field.HostField;
import de.solarreader.core.frontend.ui.HtmlInputType;
import de.solarreader.core.frontend.ui.HtmlWidth;
import de.solarreader.core.frontend.ui.UIInputElementBuilder;
import de.solarreader.core.frontend.ui.UIList;
import de.solarreader.core.frontend.ui.UITextElementBuilder;
import de.solarreader.core.plugin.AbstractHostPlugin;
import de.solarreader.core.table.TableConfiguration;
import de.solarreader.core.util.JsonFlattener;
import de.solarreader.core.util.StringConverter;
import de.solarreader.core.util.UrlBuilder;
import de.solarreader.core.value.Converter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import org.tinylog.Logger;

/**
 * The {@code Awattar} class is an implementation of {@link AbstractHostPlugin}.
 *
 * <p>This class is designed to interact with the Awattar API, a service that provides electricity
 * price information based on the current market conditions. It uses HTTP connections to communicate with the API and
 * retrieve data. The default connection mechanism utilizes an instance of {@link HostConnectionFactory}, but a custom
 * {@link ConnectionFactory} can be specified if needed.
 *
 * <p>The class also manages an internal {@code offset}, which is initialized to {@code
 * BigDecimal.ZERO} by default. This offset can be used for price adjustments or other calculations based on the data
 * retrieved from the API.
 */
public class Awattar extends AbstractHostPlugin {
  private static final String AWATTAR_PRICE = "awattar_price";

  private final BigDecimal offset;

  private final ResourceBundle resourceBundle;

  /**
   * Default constructor for the {@code Awattar} class.
   *
   * <p>This constructor creates a new instance of {@code Awattar} using a default implementation of
   * {@link HostConnectionFactory}.
   */
  public Awattar(Configuration config, EnvironmentProfile environmentProfile) {
    this(new HostConnectionFactory(), config, environmentProfile);
  }

  public Awattar(
      HostConnectionFactory connectionFactory,
      Configuration config,
      EnvironmentProfile environmentProfile) {
    super(connectionFactory, config, environmentProfile);
    this.offset =
        config.extraSettings().isPresent()
            ? (BigDecimal) config.extraSettings().get().getOrDefault("offset", BigDecimal.ZERO)
            : BigDecimal.ZERO;

    this.resourceBundle = ResourceBundle.getBundle("awattar", environmentProfile.locale());
    Logger.debug("instantiate {}", this.getClass().getName());
  }

  @Override
  public Optional<UIList> installDialog() {
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
  public List<Field> defaultReadableFields() {
    return loadFields("awattar_fields.yaml").orElse(Collections.emptyList());
  }

  @Override
  public List<TableConfiguration> defaultExportTables() {
    return loadTables("awattar_tables.yaml").orElse(Collections.emptyList());
  }

  @Override
  public Configuration defaultConfiguration() {
    return HostConfig.Builder.withDefaults()
        .withHost("api.awattar.de")
        .withReadTimeoutMillis(5000)
        .withExtraSettings(Map.of(AWATTAR_PRICE, "0.00"))
        .build();
  }

  @Override
  public Result verifyConnection(HostConnection connection) {
    String testUrl = UrlBuilder.buildUrl(hostConfig);
    try {
      connection.test(UrlBuilder.buildUri(testUrl), CONTENT_TYPE_JSON);
    } catch (IOException e) {
      return Result.error(e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.error(e.getMessage());
    }
    return Result.success(resourceBundle.getString("awattar.connection.successful"));
  }

  @Override
  protected void processHostField(
      HostConnection connection, HostField hostField, Map<String, Object> variables)
      throws IOException, InterruptedException {
    variables.put("offset", offset);
    Map<String, String> placeHolderMap = new HashMap<>();
    ZonedDateTime localDateTime = ZonedDateTime.now(environmentProfile.zoneId());
    long epochMillis = localDateTime.toInstant().toEpochMilli();
    placeHolderMap.put("epochMillis", String.valueOf(epochMillis));
    placeHolderMap.put("provider_host", hostConfig.host());
    String url = new StringConverter(hostField.url()).replaceNamedPlaceholders(placeHolderMap);
    String json = connection.getAsString(UrlBuilder.buildUri(url));
    Map<String, String> map = JsonFlattener.flatten(json);
    Converter.convertAndPopulateIndexedVariables(hostField.dataLayout().values(), map, variables);
    // Add missing hourly market price fields to enable correct value and offset calculation
    for (int hour = 0; hour < 24; hour++) {
      String key = String.format("data_%d_marketprice", hour);
      variables.putIfAbsent(key, BigDecimal.ZERO);
    }
  }
}
