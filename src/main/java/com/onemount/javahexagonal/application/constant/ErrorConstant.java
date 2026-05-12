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
    // Workflow
    public static final int WORKFLOW_TENANT_ID_REQUIRED = 400011;
    public static final int WORKFLOW_NAME_REQUIRED = 400012;
    public static final int WORKFLOW_NAME_TOO_LONG = 400013;
    public static final int WORKFLOW_VERSION_REQUIRED = 400014;
    public static final int WORKFLOW_VERSION_INVALID = 400015;
    public static final int WORKFLOW_STATUS_INVALID = 400016;
    public static final int WORKFLOW_DEFINITION_REQUIRED = 400017;
    public static final int WORKFLOW_ALREADY_EXISTS = 400018;
    public static final int WORKFLOW_DEFINITION_INVALID = 400019;
    public static final int WORKFLOW_START_NODE_REQUIRED = 400020;
    public static final int WORKFLOW_NODES_REQUIRED = 400021;
    public static final int WORKFLOW_EDGES_REQUIRED = 400022;
    public static final int WORKFLOW_NODE_ID_REQUIRED = 400023;
    public static final int WORKFLOW_NODE_TYPE_REQUIRED = 400024;
    public static final int WORKFLOW_EDGE_FROM_REQUIRED = 400025;
    public static final int WORKFLOW_EDGE_TO_REQUIRED = 400026;

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
