package com.ingesta;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija la configuracion de persistencia que se aplica en Azure sin ninguna intervencion
 * manual.
 *
 * El App Service appcentinelaprodgrupo3 no define NINGUNA Application Setting de
 * persistencia: ni SPRING_PROFILES_ACTIVE, ni INGESTA_COSMOS_ENABLED, ni
 * SPRING_DATASOURCE_URL. Lo que se aplica alli es, literalmente, lo que viaja dentro del
 * JAR. Cuando el perfil por defecto era "local" y Cosmos estaba apagado en el fichero base,
 * la aplicacion arrancaba sana y con todo en memoria: cada transaccion respondia 202, cada
 * caso se abria, y nada se escribia en Cosmos ni en Azure SQL.
 *
 * Se leen los ficheros en crudo (sin resolver placeholders) porque lo que se esta
 * comprobando es justamente el valor por defecto embebido, no el valor efectivo de este
 * test -- que corre en perfil "local" y en memoria, como todos los demas.
 */
class PersistenceDefaultsTest {

    private static Properties cargar(String recurso) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = PersistenceDefaultsTest.class.getResourceAsStream(recurso)) {
            assertThat(in).as("%s debe estar en el classpath", recurso).isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void sinVariablesDeEntornoElPerfilPorDefectoEsProd() throws Exception {
        // Con "local" se activa InMemoryFraudCaseRepository en vez de JpaFraudCaseRepository,
        // y no se carga application-prod.properties, que es donde se enciende Cosmos.
        assertThat(cargar("/application.properties").getProperty("spring.profiles.active"))
                .isEqualTo("${SPRING_PROFILES_ACTIVE:prod}");
    }

    @Test
    void elPerfilProdEnciendeCosmosParaQueLasTransaccionesSePersistan() throws Exception {
        // Sin esto no se registran CosmosTransactionRepository ni
        // CosmosTransactionScoreRepository y quedan los repositorios en memoria.
        assertThat(cargar("/application-prod.properties").getProperty("spring.cloud.azure.cosmos.enabled"))
                .isEqualTo("${INGESTA_COSMOS_ENABLED:true}");
    }

    @Test
    void fueraDelPerfilProdCosmosSigueApagado() throws Exception {
        assertThat(cargar("/application.properties").getProperty("spring.cloud.azure.cosmos.enabled"))
                .isEqualTo("${INGESTA_COSMOS_ENABLED:false}");
    }

    @Test
    void elDatasourceDelPerfilProdApuntaAlServidorRealYNoAUnPlaceholder() throws Exception {
        String url = cargar("/application-prod.properties").getProperty("spring.datasource.url");

        assertThat(url).doesNotContain("<your-server>", "<your-db>");
        assertThat(url).contains("sql-centinela-prod-1785197801.database.windows.net");
        assertThat(url).contains("databaseName=sqldb-cases-prod");
        // Identidad Gestionada: sin contrasena, igual que Storage y Cosmos.
        assertThat(url).contains("authentication=ActiveDirectoryManagedIdentity");
    }

    @Test
    void elDatasourceNoLlevaUsuarioNiContrasenaPorDefecto() throws Exception {
        // El driver rechaza ActiveDirectoryManagedIdentity si se le pasa una contrasena.
        Properties base = cargar("/application.properties");
        assertThat(base.getProperty("spring.datasource.username")).isEqualTo("${SPRING_DATASOURCE_USERNAME:}");
        assertThat(base.getProperty("spring.datasource.password")).isEqualTo("${SPRING_DATASOURCE_PASSWORD:}");
        assertThat(cargar("/application-prod.properties")).doesNotContainKeys(
                "spring.datasource.username", "spring.datasource.password");
    }
}
