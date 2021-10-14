package com.simplecityapps.shuttle.error

/**
 * A Remote Service Error with a HttpStatus code.
 */
open class RemoteServiceHttpError(val statusCode: HttpStatusCode) : RemoteServiceError() {

    val isClientError: Boolean
        get() = statusCode.code in 400..499

    val isServerError: Boolean
        get() = statusCode.code in 500..599

    override fun toString(): String {
        return "RemoteServiceHttpError" +
                "\n\t- code: $statusCode)" +
                "\n\t- message: $message"
    }
}

enum class HttpStatusCode(val code: Int) {

    Unknown(-1),

    // Client Errors
    BadRequest(400),
    Unauthorized(401),
    PaymentRequired(402),
    Forbidden(403),
    NotFound(404),
    MethodNotAllowed(405),
    NotAcceptable(406),
    ProxyAuthenticationRequired(407),
    RequestTimeout(408),
    Conflict(409),
    Gone(410),
    LengthRequired(411),
    PreconditionFailed(412),
    PayloadTooLarge(413),
    UriTooLong(414),
    UnsupportedMediaType(415),
    RangeNotSatisfiable(416),
    ExpectationFailed(417),
    ImATeapot(418),
    MisdirectedRequest(421),
    UnprocessableEntity(422),
    Locked(423),
    FailedDependency(424),
    UpgradeRequired(426),
    PreconditionRequired(428),
    TooManyRequests(429),
    RequestHeaderFieldsTooLarge(431),
    UnavailableForLegalReasons(451),

    // Server Errors
    InternalServerError(500),
    NotImplemented(501),
    BadGateway(502),
    ServiceUnavailable(503),
    GatewayTimeout(504),
    HttpVersionNotSupported(505),
    VariantAlsoNegates(506),
    InsufficientStorage(507),
    LoopDetected(508),
    NotExtended(510),
    NetworkAuthenticationRequired(511);

    override fun toString(): String {
        return "$code $name)"
    }
}