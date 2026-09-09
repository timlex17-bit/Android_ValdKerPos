package com.valdker.pos.workshop;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VehicleInputDialog extends DialogFragment {

    public interface Listener{

        void onSave(String vehicleTypeCode,String vehicle,String plate);

    }

    private Listener listener;
    private String initialVehicleTypeCode = "CAR";

    public void setListener(Listener l){

        listener=l;

    }

    public void setInitialVehicleTypeCode(String vehicleTypeCode) {
        if ("MOTORCYCLE".equalsIgnoreCase(vehicleTypeCode)) {
            initialVehicleTypeCode = "MOTORCYCLE";
        } else {
            initialVehicleTypeCode = "CAR";
        }
    }

    public static VehicleInputDialog newInstance(){

        return new VehicleInputDialog();

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState){

        View v= LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_vehicle_input,null);

        EditText etVehicle=v.findViewById(R.id.etVehicle);

        EditText etPlate=v.findViewById(R.id.etPlate);

        Spinner spVehicleType=v.findViewById(R.id.spVehicleType);
        if (spVehicleType != null) {
            spVehicleType.setSelection("MOTORCYCLE".equals(initialVehicleTypeCode) ? 1 : 0);
        }

        return new MaterialAlertDialogBuilder(requireContext())

                .setTitle("Vehicle Info")

                .setView(v)

                .setPositiveButton("Save",(d,i)->{

                    if(listener!=null){

                        listener.onSave(

                                getSelectedVehicleTypeCode(spVehicleType),

                                etVehicle.getText().toString(),

                                etPlate.getText().toString()

                        );

                    }

                })

                .setNegativeButton("Cancel",null)

                .create();

    }

    private String getSelectedVehicleTypeCode(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return "CAR";
        }

        String selected = spinner.getSelectedItem().toString();
        if ("Motorcycle".equalsIgnoreCase(selected)) {
            return "MOTORCYCLE";
        }
        return "CAR";
    }

}
