package io.minebox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Collection;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.Path;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.hubspot.dropwizard.guice.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.minebox.config.ApiConfig;
import io.minebox.nbd.MineBdModule;
import io.minebox.util.GlobalErrorHandler;
import io.minebox.util.TrivialAuthenticator;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MinebdApplication extends Application<ApiConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinebdApplication.class);
    private static final Reflections REFLECTIONS = new Reflections("io.minebox");
    private GuiceBundle<ApiConfig> guiceBundle;
    private Injector injector;

    @VisibleForTesting
//    public Injector injector;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalErrorHandler());

        args = addServerString(args);
        new MinebdApplication().run(args);
    }

    private static String[] addServerString(String[] args) {
        final String[] ret = new String[args.length + 1];
        ret[0] = "server"; //dropwizard wants the word "server" at the start...
        System.arraycopy(args, 0, ret, 1, args.length);
        return ret;

    }

    @Override
    public void run(ApiConfig configuration, Environment environment) throws Exception {
        LOGGER.info("api started up");
        injector = guiceBundle.getInjector();
        JerseyEnvironment jersey = environment.jersey();
        register(environment.lifecycle(), REFLECTIONS.getSubTypesOf(Managed.class)); // registers NbdServer


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


        final TrivialAuthenticator instance = injector.getInstance(TrivialAuthenticator.class);
        environment.jersey().register(new AuthDynamicFeature(
                new BasicCredentialAuthFilter.Builder<Principal>()
                        .setAuthenticator(instance)
                        .setAuthorizer((principal, role) -> false)
                        .buildAuthFilter()));
        environment.jersey().register(RolesAllowedDynamicFeature.class);

    }

    private void register(LifecycleEnvironment lifecycle, Collection<Class<? extends Managed>> managed) {
        LOGGER.info("managing lifecycle of {} services", managed.size());
        managed.forEach(managedClass -> lifecycle.manage(injector.getInstance(managedClass)));
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

        guiceBundle = GuiceBundle.<ApiConfig>newBuilder()
                .setConfigClass(ApiConfig.class)
                .addModule(new MineBdModule())
                .build();

        bootstrap.addBundle(guiceBundle);
        SwaggerBundle<ApiConfig> swagger = new SwaggerBundle<ApiConfig>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(ApiConfig configuration) {
                return configuration.swagger;
            }
        };
        bootstrap.addBundle(swagger);
    }
}