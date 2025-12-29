package it.eng.idsa.businesslogic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;

import it.eng.idsa.businesslogic.service.TENSORConnector;
import it.eng.idsa.businesslogic.service.TENSORConnectorRegistry;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

/**
 * The App: TRUE Connector Execution Core Container Business Logic
 */
@EnableCaching
@SpringBootApplication
@EnableAsync
public class Application {
	
	public static void main(String[] args) {
		 System.setProperty("camel.springboot.main-run-controller", "true");
		 System.setProperty("camel.component.http4.use-global-ssl-context-parameters", "true");
		 System.setProperty("server.ssl.enabled", "true");
		 System.setProperty("camel.component.jetty.use-global-ssl-context-parameters", "true");
		 System.setProperty("server.error.include-stacktrace", "never");
		 System.setProperty("server.shutdown", "graceful");
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public TENSORConnectorRegistry tensorConnectorRegistry() {
		TENSORConnectorRegistry registry = new TENSORConnectorRegistry();
		// Register connector with metadata
		registry.addConnector(new TENSORConnector("LEA_ID", "Connector BE Data App API", "Pod Name",
						"Ethereum blockchain address", "Fuzzy extractor file",
						"CMS API",
						"Solid API",
						"DSP API"));
		return registry;
	}
}
