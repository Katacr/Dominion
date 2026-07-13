package cn.lunadeer.dominion.uis.menu.action;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Dispatches the `dominion:` namespace to explicitly registered plugin operations.
 */
public final class DominionActionRegistry implements ActionHandler {

    private final Map<String, RegisteredOperation> operations = new HashMap<>();

    /**
     * Registers one Dominion-owned operation without exposing reflection to YAML.
     */
    public void register(String operation, ActionHandler handler) {
        register(operation, handler, action -> {
        });
    }

    /**
     * Registers one Dominion operation with its load-time argument validator.
     */
    public void register(String operation, ActionHandler handler, ActionValidator validator) {
        String normalizedOperation = normalize(operation);
        RegisteredOperation registered = new RegisteredOperation(
                Objects.requireNonNull(handler, "handler"),
                Objects.requireNonNull(validator, "validator")
        );
        if (operations.putIfAbsent(normalizedOperation, registered) != null) {
            throw new IllegalStateException("Dominion operation already registered: " + normalizedOperation);
        }
    }

    /**
     * Resolves and executes the first token of a `dominion:` action.
     */
    @Override
    public CompletionStage<ActionResult> execute(ActionContext context, ActionSpec action) {
        String argument = context.resolve(action.argument()).trim();
        if (argument.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "Missing Dominion operation at " + action.configPath()));
        }
        int separator = argument.indexOf(' ');
        String operation = separator < 0 ? argument : argument.substring(0, separator);
        String operationArguments = separator < 0 ? "" : argument.substring(separator + 1).trim();
        RegisteredOperation registered = operations.get(normalize(operation));
        if (registered == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "Unknown Dominion operation '" + operation + "' at " + action.configPath()));
        }
        return registered.handler().execute(
                context, new ActionSpec("dominion", operationArguments, action.configPath()));
    }

    /**
     * Validates the operation name and its operation-specific arguments during menu loading.
     */
    public void validate(ActionSpec action) {
        ParsedOperation parsed = parse(action);
        RegisteredOperation registered = operations.get(normalize(parsed.operation()));
        if (registered == null) {
            throw new IllegalArgumentException("Unknown Dominion operation '" + parsed.operation() + "' at "
                    + action.configPath());
        }
        registered.validator().validate(new ActionSpec("dominion", parsed.arguments(), action.configPath()));
    }

    private String normalize(String operation) {
        return operation.trim().toLowerCase(Locale.ROOT);
    }

    private ParsedOperation parse(ActionSpec action) {
        String argument = action.argument().trim();
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("Missing Dominion operation at " + action.configPath());
        }
        int separator = argument.indexOf(' ');
        return new ParsedOperation(
                separator < 0 ? argument : argument.substring(0, separator),
                separator < 0 ? "" : argument.substring(separator + 1).trim()
        );
    }

    private record RegisteredOperation(ActionHandler handler, ActionValidator validator) {
    }

    private record ParsedOperation(String operation, String arguments) {
    }
}
