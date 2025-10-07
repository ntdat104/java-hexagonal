package com.onemount.javahexagonal.application.constant;

public class ErrorConstant {

    private ErrorConstant() {}

    /**
     * Write the error code prefixed with 200 below
     * 200
     */
    public static final int SUCCESS = 200000;

    /**
     * Write the error code prefixed with 400 below
     * 400
     */
    public static final int FULL_NAME_REQUIRED = 400001;
    public static final int FULL_NAME_TOO_LONG = 400002;
    public static final int IS_MALE_REQUIRED = 400003;
    public static final int EMAIL_REQUIRED = 400004;
    public static final int INVALID_EMAIL_FORMAT = 400005;
    public static final int PHONE_NUMBER_REQUIRED = 400006;
    public static final int INVALID_PHONE_NUMBER_FORMAT = 400007;
    public static final int IMAGE_URL_TOO_LONG = 400008;
    public static final int ID_REQUIRED = 400009;
    public static final int INVALID_PARAMETERS = 400010;

    /**
     * Write the error code prefixed with 401 below
     * 401
     */
    public static final int UNAUTHORIZED = 401001;

    /**
     * Write the error code prefixed with 403 below
     * 403
     */
    public static final int FORBIDDEN = 403001;

    /**
     * Write the error code prefixed with 404 below
     * 404
     */
    public static final int NOT_FOUND = 404001;
    public static final int LIMIT_CONFIG_NOT_FOUND = 404002;
    public static final int TRANSACTION_NOT_FOUND = 404003;

    /**
     * Write the error code prefixed with 500 below
     * 500
     */
    public static final int INTERNAL_SERVER_ERROR = 500001;
}
