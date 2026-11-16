package ai.indra.backend;

import ai.indra.backend.http.ApiException;
import ai.indra.backend.store.Repository;
import ai.indra.core.license.LicenseKeys;
import ai.indra.core.model.LicenseRecord;
import ai.indra.core.model.Role;
import ai.indra.core.model.User;
import ai.indra.core.security.PasswordHasher;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Log;

import java.io.File;

/**
 * INDRA AI backend entry point.
 *
 * <pre>
 *   java -jar indra-backend-all.jar serve
 *   java -jar indra-backend-all.jar create-admin owner@example.com
 *   java -jar indra-backend-all.jar issue-license personal 365
 *   java -jar indra-backend-all.jar backup ./backup.json
 *   java -jar indra-backend-all.jar restore ./backup.json
 * </pre>
 */
public final class Main {

    public static final String VERSION = "1.1.0";

    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length == 0 ? "serve" : args[0].toLowerCase(java.util.Locale.ROOT);
        BackendConfig config = BackendConfig.fromEnvironment();
        switch (command) {
            case "serve":
                serve(config);
                break;
            case "create-admin":
                createAdmin(config, args);
                break;
            case "issue-license":
                issueLicense(config, args);
                break;
            case "backup":
                backup(config, args);
                break;
            case "restore":
                restore(config, args);
                break;
            case "license-keygen":
                System.out.println(LicenseKeys.generate());
                break;
            case "version":
            case "--version":
                System.out.println("INDRA AI backend " + VERSION + " - created by Umesh Chaudhary");
                break;
            case "help":
            case "--help":
            case "-h":
                printHelp();
                break;
            default:
                System.err.println("Unknown command: " + command);
                printHelp();
                System.exit(2);
        }
    }

    private static void printHelp() {
        System.out.println("INDRA AI backend " + VERSION);
        System.out.println("Commands:");
        System.out.println("  serve                                   Start the HTTP API (default)");
        System.out.println("  create-admin <email> [password]         Create or promote an administrator");
        System.out.println("  issue-license [plan] [days]             Issue a licence key");
        System.out.println("  backup <file.json>                      Export all data");
        System.out.println("  restore <file.json>                     Import a backup");
        System.out.println("  license-keygen                          Print one new licence key");
        System.out.println("  version | help");
        System.out.println();
        System.out.println("Environment: INDRA_PORT, INDRA_DATA_DIR, INDRA_DB_URL, INDRA_JWT_SECRET,");
        System.out.println("  INDRA_MASTER_KEY, INDRA_ADMIN_EMAIL, INDRA_ADMIN_PASSWORD, provider keys...");
    }

    private static void serve(BackendConfig config) {
        File dataDir = config.getDataDir();
        Log.info("Main", "INDRA AI backend " + VERSION + " starting - data directory: " + dataDir.getAbsolutePath());
        ApiApplication application = new ApiApplication(config);
        application.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                Log.info("Main", "Shutting down INDRA AI backend");
                application.stop();
            }
        }, "indra-shutdown"));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void createAdmin(BackendConfig config, String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: create-admin <email> [password]");
            System.exit(2);
        }
        ApiApplication application = new ApiApplication(config);
        Repository repository = application.getRepository();
        String email = Validation.requireEmail("email", args[1]);
        String password = args.length > 2 ? args[2] : config.getBootstrapAdminPassword();
        if (password == null) {
            password = ai.indra.core.security.Crypto.randomToken(18) + "9aA";
            System.out.println("Generated password: " + password);
            System.out.println("Change it after signing in, and store it safely.");
        }
        Validation.requirePassword("password", password);
        PasswordHasher hasher = new PasswordHasher();
        User existing = repository.findUserByEmail(email);
        if (existing == null) {
            User admin = User.newUser(email, "Administrator", hasher.hash(password));
            admin.setRole(Role.ADMIN);
            admin.setEmailVerified(true);
            repository.saveUser(admin);
            repository.audit(admin.getId(), "admin.cli.create", email, "cli");
            System.out.println("Administrator created: " + email);
        } else {
            existing.setRole(Role.ADMIN);
            existing.setDisabled(false);
            existing.setPasswordHash(hasher.hash(password));
            repository.saveUser(existing);
            repository.audit(existing.getId(), "admin.cli.reset", email, "cli");
            System.out.println("Administrator updated: " + email);
        }
        System.out.println("Sign in from the app with that email and password, then change the password.");
        application.stop();
    }

    private static void issueLicense(BackendConfig config, String[] args) {
        String plan = args.length > 1 ? args[1] : "personal";
        int days = 0;
        if (args.length > 2) {
            try {
                days = Integer.parseInt(args[2]);
            } catch (NumberFormatException error) {
                System.err.println("Invalid number of days: " + args[2]);
                System.exit(2);
            }
        }
        ApiApplication application = new ApiApplication(config);
        Repository repository = application.getRepository();
        LicenseRecord record = new LicenseRecord();
        record.setKey(LicenseKeys.generate());
        record.setPlan(plan);
        record.setIssuedBy("cli");
        record.setIssuedAt(System.currentTimeMillis());
        record.setExpiresAt(days > 0 ? System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L : 0L);
        repository.saveLicense(record);
        System.out.println("Licence key: " + record.getKey());
        System.out.println("Plan: " + plan + (days > 0 ? ", expires in " + days + " days" : ", no expiry"));
        System.out.println("Activate it in the app: Settings → Licence.");
        application.stop();
    }

    private static void backup(BackendConfig config, String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: backup <file.json>");
            System.exit(2);
        }
        ApiApplication application = new ApiApplication(config);
        BackupService service = new BackupService(application.getRepository().getStore());
        System.out.println(service.export(args[1]).toJson());
        application.stop();
    }

    private static void restore(BackendConfig config, String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: restore <file.json>");
            System.exit(2);
        }
        ApiApplication application = new ApiApplication(config);
        BackupService service = new BackupService(application.getRepository().getStore());
        try {
            System.out.println(service.restore(args[1]).toJson());
        } catch (ApiException error) {
            System.err.println(error.getMessage());
        } finally {
            application.stop();
        }
    }
}
