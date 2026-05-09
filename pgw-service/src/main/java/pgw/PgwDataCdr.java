package pgw;

public class PgwDataCdr {

    public String recordType = "PGW_DATA";

    public String servedIMSI;
    public String servedMSISDN;

    public String apn;
    public String pdpType;

    public String startTime;
    public String endTime;

    public long sessionDuration;

    public long uplinkBytes;
    public long downlinkBytes;
    public long totalBytes;

    public String ipAddress;

    public String cellId;
    public String locationAreaCode;

    public int qosClassIdentifier;

    public String terminationCause;
}