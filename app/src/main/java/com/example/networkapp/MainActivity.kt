package com.example.networkapp

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.example.networkapp.R.*
import com.example.networkapp.R.id.*

class MainActivity : AppCompatActivity() {
    private val tagPrefix = "NetworkAppLogger"

    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }

    private var channel: WifiP2pManager.Channel? = null

    private var localService: WifiP2pServiceInfo? = null
    private var serviceRequest: WifiP2pServiceRequest = WifiP2pDnsSdServiceRequest.newInstance("NetworkApp", "_netapp._tcp")
    private var localServiceAdded: Boolean = false
    private var serviceRequestAdded: Boolean = false

    private val buddies = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        channel = manager?.initialize(this, mainLooper, null)

        findViewById<Button>(server_btn).setOnClickListener {
            if(!localServiceAdded) {
                startRegistration()
                return@setOnClickListener
            }

            removeLocalService(object : WifiP2pManager.ActionListener {
                val TAG = "$tagPrefix method -removeLocalService"
                override fun onSuccess() {
                    // Success!
                    Log.d(TAG, "removeLocalService success")
                    startRegistration()
                }

                override fun onFailure(code: Int) {
                    // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    Log.e(TAG, "removeLocalService failure -$code")
                }
            })
        }

        findViewById<Button>(client_btn).setOnClickListener {
            if(!serviceRequestAdded) {
                discoverService()
                return@setOnClickListener
            }

            removeServiceRequest(object : WifiP2pManager.ActionListener {
                val TAG = "$tagPrefix method -removeServiceRequest"
                override fun onSuccess() {
                    // Success!
                    Log.d(TAG, "removeServiceRequest success")
                    discoverService()
                }

                override fun onFailure(code: Int) {
                    // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    Log.e(TAG, "removeServiceRequest failure -$code")
                }
            })
        }

        setListeners()
    }

    private fun setListeners() {
        /* Callback includes:
         * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
         * record: TXT record dta as a map of key/value pairs.
         * device: The device running the advertised service.
         */
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            val TAG = "$tagPrefix listener -txtListener"
            Log.d(TAG, "DnsSdTxtRecord available -$record")

            val log = findViewById<TextView>(client_logger)

            log.text = "fullDomanin: $fullDomain\n device: $device\n record: $record"

            record["buddyname"]?.also {
                buddies[device.deviceAddress] = it
            }
        }

        val servListener =
            WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                val TAG = "$tagPrefix listener -servListener"
                Log.d(TAG, "onBonjourServiceAvailable")
                Log.d(TAG, "instanceName -$instanceName")
                Log.d(TAG, "registrationType -$registrationType")
                Log.d(TAG, "resourceType -$resourceType")

                resourceType.deviceName =
                    buddies[resourceType.deviceAddress] ?: resourceType.deviceName

                // Add to the custom adapter defined specifically for showing
                // wifi devices.
                /*val fragment = fragmentManager
                    .findFragmentById(R.id.frag_peerlist) as WiFiDirectServicesList
                (fragment.listAdapter as WiFiDevicesAdapter).apply {
                    add(resourceType)
                    notifyDataSetChanged()
                }*/
            }

        manager?.setDnsSdResponseListeners(channel, servListener, txtListener)
    }

    @SuppressLint("MissingPermission")
    private fun startRegistration() {
        //  Create a string map containing information about your service.
        val record: Map<String, String> = mapOf(
            "listenport" to 8000.toString(),
            "buddyname" to "John Doe${(Math.random() * 1000).toInt()}",
            "available" to "visible"
        )

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        localService =
            WifiP2pDnsSdServiceInfo.newInstance("NetworkApp", "_netapp._tcp", record)

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.

        manager?.addLocalService(channel, localService, object : WifiP2pManager.ActionListener {
            val log = findViewById<TextView>(server_logger)

            override fun onSuccess() {
                log.text = "Success"
                localServiceAdded = true
            }

            override fun onFailure(arg0: Int) {
                log.text = "Fail: $arg0"
                localServiceAdded = false
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun discoverService() {
        manager?.addServiceRequest(
            channel,
            serviceRequest,
            object : WifiP2pManager.ActionListener {
                val TAG = "$tagPrefix method -addServiceRequest"
                override fun onSuccess() {
                    Log.d(TAG, "addServiceRequest success")

                    serviceRequestAdded = true

                    manager?.discoverPeers(channel,
                        object: WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d(TAG, "discoverPeers success")

                                manager?.discoverServices(
                                    channel,
                                    object : WifiP2pManager.ActionListener {
                                        val TAG = "$tagPrefix method -discoverServices()"

                                        override fun onSuccess() {
                                            // Success!
                                            Log.d(TAG, "Success")
                                        }

                                        override fun onFailure(code: Int) {
                                            Log.e(TAG, "discoverPeers failure -$code")
                                        }
                                    }
                                )
                            }

                            override fun onFailure(p0: Int) {
                                Log.e(TAG, "discoverPeers failure -$p0")
                            }
                        })
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "addServiceRequest failure -$code")

                    serviceRequestAdded = false
                }
            }
        )
    }

    private fun removeLocalService(actionListener: WifiP2pManager.ActionListener) {
        manager?.removeLocalService(channel, localService, actionListener)
    }

    private fun removeServiceRequest(actionListener: WifiP2pManager.ActionListener) {
        manager?.removeServiceRequest(channel, serviceRequest, actionListener)
    }
}