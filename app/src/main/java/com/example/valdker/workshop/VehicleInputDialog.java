package com.example.valdker.workshop;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VehicleInputDialog extends DialogFragment {

    public interface Listener{

        void onSave(String vehicle,String plate);

    }

    private Listener listener;

    public void setListener(Listener l){

        listener=l;

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

        return new MaterialAlertDialogBuilder(requireContext())

                .setTitle("Vehicle Info")

                .setView(v)

                .setPositiveButton("Save",(d,i)->{

                    if(listener!=null){

                        listener.onSave(

                                etVehicle.getText().toString(),

                                etPlate.getText().toString()

                        );

                    }

                })

                .setNegativeButton("Cancel",null)

                .create();

    }

}