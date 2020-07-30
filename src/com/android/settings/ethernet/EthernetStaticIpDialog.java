/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.ethernet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

import com.android.settings.R;

import java.util.ArrayList;

class EthernetStaticIpDialog extends AlertDialog {

	private static final String TAG = "EthernetStaticIpDialog";

	static final int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;
	static final int BUTTON_CANCEL = DialogInterface.BUTTON_NEGATIVE;

	private class IpValidator implements TextWatcher {
		private EditText view = null;

		public IpValidator(EditText view) {
			this.view = view;
			this.view.addTextChangedListener(this);
		}

		public void release() {
			view.removeTextChangedListener(this);
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			// NOP - Logic implemented on afterTextChanged
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			// NOP - Logic implemented on afterTextChanged
		}

		@Override
		public void afterTextChanged(Editable s) {
			if(!Patterns.IP_ADDRESS.matcher(s.toString()).matches())
			{
				view.setError(view.getContext().getString(R.string.ethernet_ip_settings_invalid_ip));
			}
		}
	}

	private ArrayList<IpValidator> validators = new ArrayList<>();

	private DialogInterface.OnClickListener listener = null;

	private EditText etIpAddress = null;
	private EditText etNetmask = null;
	private EditText etGateway = null;
	private EditText etDns1 = null;
	private EditText etDns2 = null;

	EthernetStaticIpDialog(Context context, DialogInterface.OnClickListener listener) {
        super(context);
		this.listener = listener;
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		View view = getLayoutInflater().inflate(R.layout.static_ip_dialog, null);
		setView(view);

		setButton(BUTTON_SUBMIT, getContext().getString(R.string.ethernet_connect), listener);
		setButton(BUTTON_CANCEL, getContext().getString(R.string.ethernet_cancel), listener);

		setTitle(getContext().getString(R.string.ethernet_settings_title));

		etIpAddress = setupField(view, R.id.ipaddress);
		etNetmask = setupField(view, R.id.network_prefix_length);
		etGateway = setupField(view, R.id.gateway);
		etDns1 = setupField(view, R.id.dns1);
		etDns2 = setupField(view, R.id.dns2);

		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStop() {
		super.onStop();
		for(IpValidator watcher : validators) {
			watcher.release();
		}
		validators.clear();
	}

	private EditText setupField(View view, int id) {
		EditText result = view.findViewById(id);
		validators.add(new IpValidator(result));
		return result;
	}

	String getIPAddress() {
		return etIpAddress.getText().toString();
	}

	String getNetmask() {
		return etNetmask.getText().toString();
	}

	String getGateway() {
		return etGateway.getText().toString();
	}
	String getDNS1() {
		return etDns1.getText().toString();
	}
	String getDNS2() {
		return etDns2.getText().toString();
	}
}


