sap.ui.define([
    "medunited/base/controller/AbstractController",
    "sap/m/MessageToast",
    "sap/ui/model/json/JSONModel"
], function (AbstractController, MessageToast, JSONModel) {
    "use strict";

    return AbstractController.extend("medunited.care.controller.SettingsDialog", {
        onInit: function () {
            this._loadKonnektorConfigs();
            this._loadGetCardsResponse();
            this._previousSMCBKey = null;
            this._telematikIdAvailable = !!localStorage.getItem("telematikId");
			this.byId("cardOptionsComboBox").setSelectedKey(localStorage.getItem("iccsn"));
        },
        onSavePress: function () {
            var oKonnektorComboBox = this.byId("settingsKonnektorComboBox");
            var sSelectedKonnektor = oKonnektorComboBox.getSelectedKey();
            var oSmcbComboBox = this.byId("cardOptionsComboBox");
            var sSelectedSmcb = oSmcbComboBox.getSelectedKey();

            if (!this._telematikIdAvailable && !sSelectedSmcb) {
                MessageToast.show(this.translate("pleaseSelectAnSmcbAndSaveBeforeProceeding"));

                var oCardOptionsContent = this.byId("cardOptionsContent");
                var oKonnektorOptionsContent = this.byId("konnektorOptionsContent");
                oKonnektorOptionsContent.setVisible(false);
                oCardOptionsContent.setVisible(true);
                this.byId("menuOptions").setSelectedKey("CardOptions");
                return;
            }

            if (this._previousSMCBKey !== sSelectedSmcb) {
                var sQueryUrl = "/telematik/id?iccsn=" + encodeURIComponent(sSelectedSmcb);

                jQuery.ajax({
                    url: sQueryUrl,
                    method: "GET",
                    success: function (oData) {
                        var sRawResponse = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);
                        console.log("Telematik ID Response:", sRawResponse);

                        localStorage.setItem("telematikId", sRawResponse);
						localStorage.setItem("iccsn", sSelectedSmcb);

                        sap.ui.getCore().getEventBus().publish("WebdavModel", "TelematikIdUpdated", {
                            telematikId: sRawResponse
                        });
                    },
                    error: function (oError) {
                        console.error("Error fetching Telematik ID:", oError);
                    }
                });
            }
            this._previousSMCBKey = sSelectedSmcb;

            this.byId("settingsDialog").close();
        },
        onCancelPress: function () {
            if (!this._telematikIdAvailable) {
                MessageToast.show(this.translate("pleaseSelectAnSmcbAndSaveBeforeProceeding"));

                var oCardOptionsContent = this.byId("cardOptionsContent");
                var oKonnektorOptionsContent = this.byId("konnektorOptionsContent");
                oKonnektorOptionsContent.setVisible(false);
                oCardOptionsContent.setVisible(true);
                this.byId("menuOptions").setSelectedKey("CardOptions");
                return;
            }
            this.byId("settingsDialog").close();
        },
        _loadKonnektorConfigs: function () {
            var oKonnektorConfigsModel = new JSONModel();
            var sUrl = "/konnektor/configs";

            jQuery.ajax({
                url: sUrl,
                method: "GET",
                headers: {
                    "Accept": "application/xml"
                },
                success: function (oData) {
                    var sXml = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);

                    console.log("Raw XML Response:", sXml);

                    var aOptions = this._parseKonnektorConfigXmlToJson(sXml);
                    console.log("Parsed connectorBaseURL Options:", aOptions);

                    if (aOptions.length === 0) {
                        console.warn("No connectorBaseURL options were found in the response.");
                    }

                    oKonnektorConfigsModel.setData({
                        options: aOptions,
                        selectedKey: aOptions.length > 0 ? aOptions[0].key : null
                    });
                    this.getView().setModel(oKonnektorConfigsModel, "konnektorComboBoxModel");

                    console.log("KonnektorComboBox Model Data:", oKonnektorConfigsModel.getData());

                    var oKonnektorComboBox = this.byId("settingsKonnektorComboBox");
                        oKonnektorComboBox.bindItems({
                            path: "konnektorComboBoxModel>/options",
                            template: new sap.ui.core.Item({
                                key: "{konnektorComboBoxModel>key}",
                                text: "{konnektorComboBoxModel>text}"
                            })
                        });

                    oKonnektorConfigsModel.refresh();
                }.bind(this),
                error: function (oError) {
                    console.error("Error fetching configs:", oError);
                }
            });
        },
        _parseKonnektorConfigXmlToJson: function (sXml) {
            if (!sXml) {
                console.error("Invalid or empty XML data.");
                return [];
            }

            var parser = new DOMParser();
            var xmlDoc;

            try {
                xmlDoc = parser.parseFromString(sXml, "application/xml");
            } catch (e) {
                console.error("XML Parsing Error:", e.message);
                return [];
            }

            if (xmlDoc.getElementsByTagName("parsererror").length > 0) {
                console.error(
                    "XML Parsing Error:",
                    xmlDoc.getElementsByTagName("parsererror")[0]?.textContent || "Unknown error"
                );
                return [];
            }

            var aConfigs = [];
            var aKonnektorConfigs = xmlDoc.querySelectorAll("collection > KonnektorConfig");

            if (aKonnektorConfigs.length === 0) {
                console.warn("No KonnektorConfig elements found in the response.");
                return [];
            }

            aKonnektorConfigs.forEach(function (konnektorConfig) {
                var oConfig = {};
                Array.from(konnektorConfig.childNodes).forEach(function (oNode) {
                    if (oNode.nodeType === Node.ELEMENT_NODE) {
                        oConfig[oNode.tagName] = oNode.textContent.trim();
                    }
                });
                aConfigs.push(oConfig);
            });

            return aConfigs.map(config => ({
                key: config.connectorBaseURL,
                text: config.connectorBaseURL,
            }));
        },
        _loadGetCardsResponse: function () {
            var oGetCardsResponseModel = new JSONModel();
            var sUrl = "/event/cards";

            jQuery.ajax({
                url: sUrl,
                method: "GET",
                headers: {
                    "Accept": "application/xml"
                },
                success: function (oData) {
                    var sXml = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);

                    var aOptions = this._parseGetCardsResponseXmlToJson(sXml);

                    if (aOptions.length === 0) {
                        console.warn("No SMC-B card options were found in the response.");
                    }

                    oGetCardsResponseModel.setData({
                        options: aOptions
                    });
                    this.getView().setModel(oGetCardsResponseModel, "comboBoxModel");

                }.bind(this),
                error: function (oError) {
                    console.error("Error fetching configs:", oError);
                }
            });
        },
        _parseGetCardsResponseXmlToJson: function (sXml) {
            var oParser = new DOMParser();
            var oXmlDoc = oParser.parseFromString(sXml, "application/xml");
            var aCards = [];

            var oXPathResult = oXmlDoc.evaluate(
                "//ns5:Card[ns4:CardType='SMC-B']",
                oXmlDoc,
                function (prefix) {
                    if (prefix === "ns5") return "http://ws.gematik.de/conn/CardService/v8.1";
                    if (prefix === "ns4") return "http://ws.gematik.de/conn/CardServiceCommon/v2.0";
                    return null;
                },
                XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
                null
            );

            for (var i = 0; i < oXPathResult.snapshotLength; i++) {
                var oCard = oXPathResult.snapshotItem(i);
                var sCardHandle = oCard.getElementsByTagNameNS("http://ws.gematik.de/conn/ConnectorCommon/v5.0", "CardHandle")[0].textContent;
                var sCardHolderName = oCard.getElementsByTagNameNS("http://ws.gematik.de/conn/CardService/v8.1", "CardHolderName")[0].textContent;
                var sCardIccsn = oCard.getElementsByTagNameNS("http://ws.gematik.de/conn/CardServiceCommon/v2.0", "Iccsn")[0].textContent;

                aCards.push({
                    key: sCardIccsn,
                    text: sCardHolderName
                });
            }

            return aCards;
        },
        _loadGetCardTerminalsResponse: function () {
            var oCardTerminalModel = new JSONModel();
            var sUrl = "/event/cardterminals";

            jQuery.ajax({
                url: sUrl,
                method: "GET",
                headers: {
                    "Accept": "application/xml"
                },
                success: function (oData) {
                    var sXml = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);

                    var aTerminals = this._parseGetCardTerminalsResponseXmlToJson(sXml);

                    if (aTerminals.length === 0) {
                        console.warn("No card terminals were found in the response.");
                    }

                    var selectedKeys = aTerminals.map(function (item) {
                        return item.key;
                    });

                    oCardTerminalModel.setData({
                        options: aTerminals,
                        selectedKeys: selectedKeys
                    });

                    this.getView().setModel(oCardTerminalModel, "cardTerminalMultiComboBoxModel");

                }.bind(this),
                error: function (oError) {
                    console.error("Error fetching card terminals:", oError);
                }
            });
        },
        _parseGetCardTerminalsResponseXmlToJson: function (sXml) {
            var oParser = new DOMParser();
            var oXmlDoc = oParser.parseFromString(sXml, "application/xml");
            var aTerminals = [];

            var oXPathResult = oXmlDoc.evaluate(
                "//ns8:CardTerminal",
                oXmlDoc,
                function (prefix) {
                    if (prefix === "ns8") return "http://ws.gematik.de/conn/CardTerminalInfo/v8.0";
                    if (prefix === "ns4") return "http://ws.gematik.de/conn/CardServiceCommon/v2.0";
                    return null;
                },
                XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
                null
            );

            for (var i = 0; i < oXPathResult.snapshotLength; i++) {
                var oTerminal = oXPathResult.snapshotItem(i);
                var sCtId = oTerminal.getElementsByTagNameNS("http://ws.gematik.de/conn/CardServiceCommon/v2.0", "CtId")[0]?.textContent || "Unbekannt";
                var sName = oTerminal.getElementsByTagNameNS("http://ws.gematik.de/conn/CardTerminalInfo/v8.0", "Name")[0]?.textContent || "Unbekannt";
                var sIpAddress = oTerminal.getElementsByTagNameNS("http://ws.gematik.de/conn/CardTerminalInfo/v8.0", "IPV4Address")[0]?.textContent || "Unbekannt";

                aTerminals.push({
                    key: sCtId,
                    text: `${sName} (${sIpAddress})`
                });
            }

            return aTerminals;
        },
        formatKeyAndText: function (sKey, sText) {
            return sKey + " - " + sText;
        },
        onMenuOptionChange: function (oEvent) {
            var sSelectedKey = oEvent.getSource().getSelectedKey();

            var oKonnektorOptionsContent = this.byId("konnektorOptionsContent");
            var oCardOptionsContent = this.byId("cardOptionsContent");
            var oCardTerminalOptionsContent = this.byId("cardTerminalOptionsContent")

            if (sSelectedKey === "KonnektorOptions") {
                oKonnektorOptionsContent.setVisible(true);
                oCardOptionsContent.setVisible(false);
                oCardTerminalOptionsContent.setVisible(false);

                this._loadKonnektorConfigs();

            } else if (sSelectedKey === "CardOptions") {
                oKonnektorOptionsContent.setVisible(false);
                oCardOptionsContent.setVisible(true);
                oCardTerminalOptionsContent.setVisible(false);

                this._loadGetCardsResponse();

            } else if (sSelectedKey === "CardTerminalOptions") {
                oKonnektorOptionsContent.setVisible(false);
                oCardOptionsContent.setVisible(false);
                oCardTerminalOptionsContent.setVisible(true);

                this._loadGetCardTerminalsResponse();
            }
        }
    });
});
