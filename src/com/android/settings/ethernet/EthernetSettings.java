/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.EthernetManager;
import android.net.IpConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.StaticIpConfiguration;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v14.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.net.Inet4Address;
import java.net.InetAddress;

public class EthernetSettings extends SettingsPreferenceFragment implements
        DialogInterface.OnClickListener,
        Preference.OnPreferenceChangeListener
{
    private static final String TAG = "EthernetSettings";

    private static final String KEY_ETH_IP_ADDRESS = "ethernet_ip_addr";
    private static final String KEY_ETH_NET_MASK = "ethernet_netmask";
    private static final String KEY_ETH_GATEWAY = "ethernet_gateway";
    private static final String KEY_ETH_DNS1 = "ethernet_dns1";
    private static final String KEY_ETH_DNS2 = "ethernet_dns2";
    private static final String KEY_ETH_MODE = "ethernet_mode_select";

    private static final String NULL_IP_ADDR = "0.0.0.0";

    private static String [] DNS_MAP = { KEY_ETH_DNS1, KEY_ETH_DNS2 };

    private static final int SHOW_STATIC_IP_DIALOG = 0;

    private SwitchPreference mode_preference = null;

    private ConnectivityManager connectivityManager = null;
    private EthernetManager ethernetManager = null;

    private String iface = null;

    private EthernetStaticIpDialog dialog = null;

    class NetworkStatus extends ConnectivityManager.NetworkCallback {
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties);
            getActivity().runOnUiThread(() -> updatePreferences());
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.WIFI_TETHER_SETTINGS;
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if(dialogId == SHOW_STATIC_IP_DIALOG) {
            return MetricsProto.MetricsEvent.WIFI_TETHER_SETTINGS;
        }
        return super.getDialogMetricsCategory(dialogId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ethernet_settings);

        ethernetManager = (EthernetManager) getSystemService(Context.ETHERNET_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        resetPreferences();
        updatePreferences();

        if(mode_preference != null) {
            mode_preference.setOnPreferenceChangeListener(this);
        }

        NetworkRequest request = new NetworkRequest.Builder().build();
        connectivityManager.registerNetworkCallback(request, new NetworkStatus());
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if(mode_preference != null) {
            mode_preference.setOnPreferenceChangeListener(null);
        }
    }

    private void resetPreferences() {
        setPreference(KEY_ETH_IP_ADDRESS, NULL_IP_ADDR);
        setPreference(KEY_ETH_NET_MASK, NULL_IP_ADDR);
        setPreference(KEY_ETH_GATEWAY, NULL_IP_ADDR);
        setPreference(KEY_ETH_DNS1, NULL_IP_ADDR);
        setPreference(KEY_ETH_DNS2, NULL_IP_ADDR);
        setPreference(KEY_ETH_MODE, "Unknown");
    }

    private void updatePreferences() {
        if(ethernetManager == null) {
            Log.w(TAG, "Could not connect to EthernetManager !");
            return;
        }

        String [] ethernetIFaces = ethernetManager.getAvailableInterfaces();
        iface = ethernetIFaces[0];

        IpConfiguration ipConfiguration = ethernetManager.getConfiguration(iface);
        IpConfiguration.IpAssignment ipAssignment = ipConfiguration.getIpAssignment();

        mode_preference = (SwitchPreference) findPreference(KEY_ETH_MODE);

        if(ipAssignment == IpConfiguration.IpAssignment.DHCP ||
                ipAssignment == IpConfiguration.IpAssignment.UNASSIGNED)
        {
            Log.d(TAG, "Parsing DHCP settings !");
            parseDHCP(iface);
            mode_preference.setChecked(true);
        }
        else if (ipConfiguration.getIpAssignment() == IpConfiguration.IpAssignment.STATIC)
        {
            Log.d(TAG, "Parsing Static IP settings !");
            parseStatic(ipConfiguration.getStaticIpConfiguration());
            mode_preference.setChecked(false);
        }
    }

    private void parseDHCP(String name) {
        if(connectivityManager == null) {
            Log.w(TAG, "Could not connect to the Connectivity Manager !");
            return;
        }

        LinkProperties linkProperties = findLinkProperties(connectivityManager, name);
        if (linkProperties == null) {
            Log.w(TAG, "Could not find ethernet with name : " + name);
            return;
        }

        updateIPAddress(linkProperties);
        updateNetMask(linkProperties);
        updateGateway(linkProperties);
        updateDNSServers(linkProperties);
    }

    private LinkProperties findLinkProperties(ConnectivityManager manager, String iface) {
        for(Network network : manager.getAllNetworks())
        {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            LinkProperties properties = manager.getLinkProperties(network);

            if(properties.getInterfaceName().compareTo(iface) == 0) {
                return properties;
            }
        }

        return null;
    }

    private void updateIPAddress(LinkProperties linkProperties) {
        // Update IP information.
        StringBuffer buffer = new StringBuffer();
        for(LinkAddress linkAddress: linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if(address.isLinkLocalAddress() || address.isLinkLocalAddress()) {
                continue;
            }
            buffer.append(linkAddress.toString());
            buffer.append("\n");
        }

        setPreference(KEY_ETH_IP_ADDRESS, buffer.toString().trim());
    }

    private void updateNetMask(LinkProperties linkProperties) {
        for(LinkAddress linkAddress: linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if(address.isAnyLocalAddress() || !linkAddress.isIPv4()) {
                continue;
            }
            setPreference(KEY_ETH_NET_MASK, calculateNetMask(linkAddress.getPrefixLength()));
            return;
        }
    }

    private void updateGateway(LinkProperties linkProperties) {
        for(RouteInfo route : linkProperties.getAllRoutes())
        {
            if(route.isIPv4Default() && route.hasGateway()) {
                setPreference(KEY_ETH_GATEWAY, route.getGateway().getHostAddress());
                return;
            }
        }
    }

    private void updateDNSServers(LinkProperties linkProperties) {
        // Update DNS servers information.
        int dns_index = 0;
        for(InetAddress address: linkProperties.getDnsServers()) {
            setPreference(DNS_MAP[dns_index], address.getHostAddress());
            if(++dns_index >= DNS_MAP.length)
                return;
        }
    }

    private String calculateNetMask(int prefix_length) {
        int netmask = NetworkUtils.prefixLengthToNetmaskInt(prefix_length);
        return NetworkUtils.intToInetAddress(netmask).getHostAddress();
    }

    private void parseStatic(StaticIpConfiguration configuration) {
        LinkProperties properties = configuration.toLinkProperties(iface);
        updateIPAddress(properties);
        updateNetMask(properties);
        updateGateway(properties);
        updateDNSServers(properties);
    }

    private void setPreference(String key, String value) {
        Preference preference = findPreference(key);
        if(preference != null)
            preference.setSummary(value);
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        if(button == DialogInterface.BUTTON_NEGATIVE) {
            Log.v(TAG, "Revert back to DHCP mode");
            mode_preference.setChecked(true);
        } else if (button == DialogInterface.BUTTON_POSITIVE) {
            Log.v(TAG, "Static IP configured !");
            try {
                IpConfiguration configuration = createStaticIPConfiguration();
                ethernetManager.setConfiguration(iface, configuration);
                parseStatic(configuration.staticIpConfiguration);
            }
            catch (IllegalArgumentException e) {
                Log.w(TAG, e);
                mode_preference.setChecked(true);
                new AlertDialog.Builder(getActivity())
                        .setTitle("Invalid Static IP settings")
                        .setMessage("Could not parse static IP parameters.\nPlease check your input.")
                        .setPositiveButton(android.R.string.ok, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        }
        dialog = null;
    }

    private IpConfiguration createStaticIPConfiguration() {
        String ipAddress = dialog.getIPAddress();
        String gateway = dialog.getGateway();
        String dns1 = dialog.getDNS1();
        String dns2 = dialog.getDNS2();

        StaticIpConfiguration config = new StaticIpConfiguration();

        InetAddress address = NetworkUtils.numericToInetAddress(ipAddress);
        int prefix = 0;
        if(address instanceof Inet4Address) {
            int netmask = 0;
            String netmaskStr = dialog.getNetmask();
            if(TextUtils.isEmpty(netmaskStr)) {
                netmask = NetworkUtils.getImplicitNetmask((Inet4Address) address);
            }
            else {
                Inet4Address netmaskAddress = (Inet4Address) NetworkUtils.numericToInetAddress(netmaskStr);
                netmask = NetworkUtils.inetAddressToInt(netmaskAddress);
            }
            prefix = NetworkUtils.netmaskIntToPrefixLength(netmask);
        }

        LinkAddress linkAddress = new LinkAddress(address, prefix);

        config.ipAddress = linkAddress;

        if(!TextUtils.isEmpty(gateway))
            config.gateway = NetworkUtils.numericToInetAddress(gateway);

        if(!TextUtils.isEmpty(dns1))
            config.dnsServers.add(NetworkUtils.numericToInetAddress(dns1));

        if(!TextUtils.isEmpty(dns2))
            config.dnsServers.add(NetworkUtils.numericToInetAddress(dns2));

        return new IpConfiguration(IpConfiguration.IpAssignment.STATIC, IpConfiguration.ProxySettings.NONE, config, null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        Log.d(TAG, preference.getKey());
        Log.d(TAG, o.toString());
        if(preference.getKey().equals(KEY_ETH_MODE)) {
            Boolean mode = (Boolean) o;
            if(mode) {
                IpConfiguration ipConfiguration = new IpConfiguration(IpConfiguration.IpAssignment.DHCP, IpConfiguration.ProxySettings.NONE, null, null);
                ethernetManager.setConfiguration(iface, ipConfiguration);
            }
            else
            {
                showDialog(SHOW_STATIC_IP_DIALOG);
            }
        }
        return true;
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if(dialogId == SHOW_STATIC_IP_DIALOG) {
            dialog = new EthernetStaticIpDialog(getActivity(), this);
            return dialog;
        }
        return super.onCreateDialog(dialogId);
    }
}
