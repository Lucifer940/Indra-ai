package ai.indra.backend.http;

/** A route handler. */
public interface Handler {

    Response handle(RequestContext context) throws Exception;
}
