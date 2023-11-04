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

    private val buddies = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        channel = manager?.initialize(this, mainLooper, null)

        findViewById<Button>(server_btn).setOnClickListener {
            manager?.clearLocalServices(channel,
                object : WifiP2pManager.ActionListener {
                    val TAG = "$tagPrefix method -removeLocalService"
                    override fun onSuccess() {
                        Log.d(TAG, "removeLocalService success")
                    }

                    override fun onFailure(code: Int) {
                        Log.e(TAG, "removeLocalService failure -$code")
                    }
                })

            startRegistration()
        }

        findViewById<Button>(client_btn).setOnClickListener {
            manager?.clearServiceRequests(channel,
                object : WifiP2pManager.ActionListener {
                    val TAG = "$tagPrefix method -removeServiceRequest"
                    override fun onSuccess() {
                        Log.d(TAG, "removeServiceRequest success")
                    }

                    override fun onFailure(code: Int) {
                        Log.e(TAG, "removeServiceRequest failure -$code")
                    }
                })

            discoverService()
        }

        setListeners()
    }

    private fun setListeners() {
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
                val TAG = "$tagPrefix listener -servListener"
                Log.d(TAG, "onBonjourServiceAvailable")
                Log.d(TAG, "instanceName -$instanceName")
                Log.d(TAG, "registrationType -$registrationType")
                Log.d(TAG, "resourceType -$resourceType")

                resourceType.deviceName =
                    buddies[resourceType.deviceAddress] ?: resourceType.deviceName
            }

        manager?.setDnsSdResponseListeners(channel, servListener, txtListener)
    }

    @SuppressLint("MissingPermission")
    private fun startRegistration() {
        val record: Map<String, String> = mapOf(
            "listenport" to 8000.toString(),
            "buddyname" to "John Doe${(Math.random() * 1000).toInt()}",
            "available" to "visible"
        )

        var localService =
            WifiP2pDnsSdServiceInfo.newInstance("NetworkApp", "_netapp._tcp", record)

        manager?.addLocalService(channel, localService, object : WifiP2pManager.ActionListener {
            val log = findViewById<TextView>(server_logger)

            override fun onSuccess() {
                log.text = "Success"
            }

            override fun onFailure(arg0: Int) {
                log.text = "Fail: $arg0"
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun discoverService() {
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

        manager?.addServiceRequest(
            channel,
            serviceRequest,
            object : WifiP2pManager.ActionListener {
                val TAG = "$tagPrefix method -addServiceRequest"
                override fun onSuccess() {
                    Log.d(TAG, "addServiceRequest success")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "addServiceRequest failure -$code")
                }
            })

        manager?.discoverServices(
            channel,
            object : WifiP2pManager.ActionListener {
                val TAG = "$tagPrefix method -discoverServices()"

                override fun onSuccess() {
                    Log.d(TAG, "Success")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "discoverPeers failure -$code")
                }
            })
    }
}