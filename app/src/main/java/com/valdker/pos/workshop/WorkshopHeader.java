package com.valdker.pos.workshop;

public class WorkshopHeader {

    public int customerId;

    public String customerName;

    public String vehicleName;

    public String vehicleTypeCode;

    public String plateNumber;

    public String status;

    public WorkshopHeader(){

        customerId=0;

        customerName="Walk-in Customer";

        vehicleName="-";

        vehicleTypeCode="CAR";

        plateNumber="-";

        status="Draft";

    }

}
