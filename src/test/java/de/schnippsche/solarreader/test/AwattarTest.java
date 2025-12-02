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
package de.schnippsche.solarreader.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

import de.schnippsche.solarreader.plugins.awattar.Awattar;
import de.solarreader.core.EnvironmentProfile;
import de.solarreader.core.Variable;
import de.solarreader.core.VariableContext;
import de.solarreader.core.config.Configuration;
import de.solarreader.core.config.HostConfig;
import de.solarreader.core.connection.host.HostConnection;
import de.solarreader.core.connection.host.HostConnectionFactory;
import de.solarreader.core.field.Field;
import de.solarreader.core.table.TableConfiguration;

class AwattarTest
{
    @Test
    void test() throws Exception
    {
        EnvironmentProfile environmentProfile = EnvironmentProfile.defaults();
        Configuration configuration = new Awattar(HostConfig.Builder.withDefaults().build(), environmentProfile).defaultConfiguration();
        HostConnectionFactory testFactory = new HostConnectionFactory()
        {
            @Override
            public HostConnection createConnection(Configuration configuration)
            {
                return new AwattarHttpConnection();
            }
        };


        Awattar provider = new Awattar(testFactory, configuration, environmentProfile);


        List< TableConfiguration> tables = provider.defaultExportTables();
        List<Field> fields = provider.defaultReadableFields();
        VariableContext variableContext = new VariableContext(List.of());
        provider.read(fields, Instant.now(),  variableContext);
        // GeneralTestHelper generalTestHelper = new GeneralTestHelper();
/*
    ProviderData providerData = new ProviderData();
    providerData.setSetting(provider.getDefaultProviderSetting());
    providerData.setPluginName("Awattar");
    providerData.setName("Awattar Test");
    provider.setProviderData(providerData);
    generalTestHelper.testProviderInterface(provider);
    Map<String, Object> variables = providerData.getResultVariables();

    System.out.println(variables);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    assert provider.getSupportedProperties().isPresent();
    JsonTools jsonTools = new JsonTools();
    String content = readResourceFile();

    Map<String, Object> originalMap = jsonTools.getSimpleMapFromJsonString(content);
    // check calculated variables with json fields
    for (int hour = 0; hour < 24; hour++) {
      String timestampStartKey = String.format("data_%d_start_timestamp", hour);
      // search original value
      if (originalMap.containsKey(timestampStartKey)) {
        String timestampStart = originalMap.get(timestampStartKey).toString();
        long epochMillis = Long.parseLong(timestampStart);
        Instant instant = Instant.ofEpochMilli(epochMillis);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of("Europe/Berlin"));
        String mustStartTime = zdt.format(formatter);
        String priceKey = String.format("data_%d_marketprice", hour);
        String price = originalMap.get(priceKey).toString();
        BigDecimal mustPrice = new BigDecimal(price);
        // find variables
        Object calculatedStartTime = variables.get(String.format("start_timestamp_hour_%d", hour));
        assert calculatedStartTime instanceof Instant;
        assert Objects.equals(instant, calculatedStartTime);
        Object calculatedPrice = variables.get(String.format("marketprice_hour_%d", hour));
        assert calculatedPrice instanceof BigDecimal;
        assert Objects.equals(mustPrice, calculatedPrice);
        System.out.printf("startTime = %s, price = %s%n", mustStartTime, price);
      }
    } */
    }

    String readResourceFile() throws IOException
    {
        String resourcePath = "awattar_response.json";
        try (InputStream inputStream =
                 AwattarTest.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                return scanner.useDelimiter("\\A").next();
            }
        }
    }
}
