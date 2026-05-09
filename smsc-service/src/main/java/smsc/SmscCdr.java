package smsc;

public class SmscCdr {

    public String recordType = "SMSC_SMS";

    public String senderIMSI;
    public String senderMSISDN;

    public String receiverMSISDN;

    public String messageType; // SMS-MO / SMS-MT

    public String submissionTime;
    public String deliveryTime;

    public String status; // DELIVERED / FAILED / EXPIRED

    public String messageSize; // bytes or chars

    public String cellId;
}