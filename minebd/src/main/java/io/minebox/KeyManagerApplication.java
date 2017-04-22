package io.minebox;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.hubspot.dropwizard.guice.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.minebox.config.ApiConfig;
import io.minebox.util.GlobalErrorHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;


public class KeyManagerApplication extends Application<ApiConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyManagerApplication.class);
    private static final Reflections REFLECTIONS = new Reflections("io.minebox");
    @VisibleForTesting
    public Injector injector;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalErrorHandler());
        if (args.length == 3) { //small hack for commit hash
            args = new String[]{args[0], args[1]};
        }
        new KeyManagerApplication().run(args);
    }

    @Override
    public void run(ApiConfig configuration, Environment environment) throws Exception {
        LOGGER.info("api started up");
        JerseyEnvironment jersey = environment.jersey();
//        injector.getInstance(SessionFactory.class); //init DB
        installCorsFilter(environment);

        //init all Singletons semi-eagerly
        REFLECTIONS.getTypesAnnotatedWith(Singleton.class).forEach(injector::getInstance);
        register(jersey, REFLECTIONS.getTypesAnnotatedWith(Path.class));


        jersey.register(new LoggingExceptionMapper<Throwable>() {
            @Override
            protected String formatErrorMessage(long id, Throwable exception) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);
                return sw.toString();
            }
        });
        jersey.register(new JsonProcessingExceptionMapper(true));
        jersey.register(new EarlyEofExceptionMapper());
    }


    private void installCorsFilter(Environment environment) {
        FilterRegistration.Dynamic filter = environment.servlets().addFilter("CORSFilter", CrossOriginFilter.class);

        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, environment.getApplicationContext().getContextPath() + "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,OPTIONS");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "Origin, Content-Type, Accept, Authorization");
        filter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");
    }

    private void register(JerseyEnvironment jersey, Iterable<Class<?>> resources) {
        for (Class<?> resourceClass : resources) {
            jersey.register(resourceClass);
        }
    }

    @Override
    public void initialize(Bootstrap<ApiConfig> bootstrap) {

        GuiceBundle<ApiConfig> guiceBundle = GuiceBundle.<ApiConfig>newBuilder()
                .setConfigClass(ApiConfig.class)
                .addModule(new AbstractModule() {
                    @Override
                    protected void configure() {
//a single module is required
                    }
                })
                .build();

        bootstrap.addBundle(guiceBundle);

        injector = guiceBundle.getInjector();

        SwaggerBundle<ApiConfig> swagger = new SwaggerBundle<ApiConfig>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(ApiConfig configuration) {
                return configuration.swaggerBundleConfiguration;
            }
        };

        bootstrap.addBundle(swagger);
        bootstrap.addBundle(new AssetsBundle("/static", "/", "index.html", "static"));
    }
}