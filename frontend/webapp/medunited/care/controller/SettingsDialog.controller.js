sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/ui/model/json/JSONModel"
], function (Controller, MessageToast, JSONModel) {
    "use strict";

    return Controller.extend("medunited.care.controller.SettingsDialog", {
        onSavePress: function () {
            var oKonnektorComboBox = this.byId("settingsKonnektorComboBox");
            var sSelectedKonnektor = oKonnektorComboBox.getSelectedKey();
            MessageToast.show("Konnektor selected option: " + sSelectedKonnektor);

            var oSmcbComboBox = this.byId("cardOptionsComboBox");
            var sSelectedSmcb = oSmcbComboBox.getSelectedKey();
            MessageToast.show("SMC-B selected option: " + sSelectedSmcb);
            var oSelectedItem = oSmcbComboBox.getSelectedItem();

            if (oSelectedItem) {
                var sSelectedIccsn = oSelectedItem.getBindingContext("comboBoxModel").getObject().iccsn;
            }

            if (this._previousSMCBKey !== sSelectedSmcb) {
                var sQueryUrl = "http://localhost:8090/telematik/id?iccsn=" + encodeURIComponent(sSelectedIccsn);
                jQuery.ajax({
                    url: sQueryUrl,
                    method: "GET",
                    success: function (oData) {
                        var sRawResponse = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);
                        console.log("Telematik ID Response:", sRawResponse);

                        localStorage.setItem("telematikId", sRawResponse);
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
            this.byId("settingsDialog").close();
        },
        onInit: function () {
            this._loadKonnektorConfigs();
            this._previousSMCBKey = null;
        },
        _loadKonnektorConfigs: function () {
            var oKonnektorConfigsModel = new JSONModel();
            var sUrl = "http://localhost:8090/konnektor/configs";

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
            var sUrl = "http://localhost:8090/event/cards";

            jQuery.ajax({
                url: sUrl,
                method: "GET",
                headers: {
                    "Accept": "application/xml"
                },
                success: function (oData) {
                    var sXml = typeof oData === "string" ? oData : new XMLSerializer().serializeToString(oData);

                    console.log("Raw XML Response:", sXml);

                    var aOptions = this._parseGetCardsResponseXmlToJson(sXml);
                    console.log("Parsed cards Options:", aOptions);

                    if (aOptions.length === 0) {
                        console.warn("No SMC-B card options were found in the response.");
                    }

                    oGetCardsResponseModel.setData({
                        options: aOptions,
                        selectedKey: aOptions.length > 0 ? aOptions[0].key : null
                    });
                    this.getView().setModel(oGetCardsResponseModel, "comboBoxModel");

                    console.log("CardsComboBox Model Data:", oGetCardsResponseModel.getData());

                    var oCardsComboBox = this.byId("cardOptionsComboBox");
                    oCardsComboBox.bindItems({
                        path: "comboBoxModel>/options",
                        template: new sap.ui.core.Item({
                            key: "{comboBoxModel>key}",
                            text: {
                                parts: [
                                    { path: 'comboBoxModel>key' },
                                    { path: 'comboBoxModel>text' }
                                ],
                                formatter: this.formatKeyAndText
                            }
                        })
                    });

                    oGetCardsResponseModel.refresh();
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
                    key: sCardHandle,
                    text: sCardHolderName,
                    iccsn: sCardIccsn
                });
            }

            return aCards;
        },
        formatKeyAndText: function (sKey, sText) {
            return sKey + " - " + sText;
        },
        onMenuOptionChange: function (oEvent) {
            var sSelectedKey = oEvent.getSource().getSelectedKey();

            var oKonnektorOptionsContent = this.byId("konnektorOptionsContent");
            var oCardOptionsContent = this.byId("cardOptionsContent");

            if (sSelectedKey === "KonnektorOptions") {
                oKonnektorOptionsContent.setVisible(true);
                oCardOptionsContent.setVisible(false);

                this._loadKonnektorConfigs();

            } else if (sSelectedKey === "CardOptions") {
                oKonnektorOptionsContent.setVisible(false);
                oCardOptionsContent.setVisible(true);

                this._loadGetCardsResponse();
            }
        }
    });
});
