sap.ui.define([
   "sap/ui/model/xml/XMLModel",
   "medunited/care/model/WebdavListBinding"
], function(XMLModel, WebdavListBinding) {

	"use strict";

	var WebdavModel = XMLModel.extend("medunited.care.model.WebdavModel", {
        constructor : function(sServiceUrl, mParameters) {
			XMLModel.apply(this);
			if (!sServiceUrl) {
				throw new Error("Missing service root URL");
			}
			sServiceUrl = sServiceUrl.slice(-1) === "/" ?  sServiceUrl.slice(0, -1) : sServiceUrl;
			this.oServiceUrl = new URI(sServiceUrl);
			this.sServiceUrl = this.oServiceUrl.query("").toString();
			this.aCallAfterUpdate = [];
			this.oDomParser = new DOMParser();
			// Set default namespace
			// Relative bindings: {D:propstat/D:prop/D:displayname} cause an issue with the XML parser
			// Absolute ones work {/D:response}
			this.setNameSpace("DAV:");
			this.setNameSpace("http://ws.gematik.de/fa/vsdm/vsd/v5.2", "vsdm");
			this.bFirstSetOfTelematikId = true;
			

			sap.ui.getCore().getEventBus().subscribe("WebdavModel", "TelematikIdUpdated", this._onTelematikIdUpdated, this);
        },
        _setupData: function() {
			let me = this;
            fetch(this.sServiceUrl, {
                "method": "PROPFIND",
                "headers": {
                    "Depth": "1"
                }
            })
			.then(o => o.text())
			.then(xml => me.setXML(xml) );
        },
		loadFolderForContext: function(sPath, sFileWithPath) {
			const me = this;
			fetch(this.sServiceUrl+sFileWithPath, {
                "method": "PROPFIND",
                "headers": {
                    "Depth": "Infinity"
                }
            })
			.then(o => o.text())
			.then(str => new window.DOMParser().parseFromString(str, "text/xml"))
			.then(xml => {
				me._addImportNode(sPath, xml);
			});
		},
		loadFileForContext: function(sPath, sFileWithPath) {
			const me = this;
			const decoder = new TextDecoder('iso-8859-1');
            fetch(this.sServiceUrl+sFileWithPath)
			.then(o => o.arrayBuffer())
			.then(buf => decoder.decode(buf))
			.then(str => new window.DOMParser().parseFromString(str, "text/xml"))
			.then(xml => {
				me._addImportNode(sPath, xml);
			});
		},
		_addImportNode: function(sPath, xml) {
			const oNodeForImport = this.getData().importNode(xml.documentElement,true);
			const oNode = this._getObject(sPath)[0];
			const oOldNode = oNode.getElementsByTagName(oNodeForImport.tagName);
			if(oOldNode.length > 0) {
				oNode.removeChild(oOldNode[0]);
			}
			oNode.appendChild(oNodeForImport);
			this.updateBindings();
		},
		_onTelematikIdUpdated: function(sChannelId, sEventId, oData) {
            if (oData && oData.telematikId && this.bFirstSetOfTelematikId) {
                this.bFirstSetOfTelematikId = false;
				this.sServiceUrl = `/webdav/${oData.telematikId}`;
				this.oData = undefined;
            } else {
				window.location.reload();
			}
        },
        readWebdavFolder: function(sPath = "", iStartIndex, iPageSize, aSorters, aFilters) {

            var sUrl = this.sServiceUrl;
            if (sPath && sPath !== "/" && sPath !== "") {
                sUrl += sPath.startsWith("/") ? sPath : "/" + sPath;
            }
			
			if(!iStartIndex) {
				iStartIndex = 0;
			}
			
			if(!iPageSize) {
				iPageSize = this.iSizeLimit;
			}

            console.log("Url:", sUrl);

            var headers = {
                "Depth": "1",
                "X-Offset": String(iStartIndex),
                "X-Limit": String(iPageSize),
                "X-Sort-By": "Earliest"
            };

            return fetch(sUrl, { method: "PROPFIND", headers: headers })
                .then(response => {
                    if (!response.ok) {
                        throw new Error("response was not ok: " + response.statusText);
                    }
					this.fireRequestCompleted({});
                    return response.text().then(xml => {
						if(!this.oData) {
							this.setXML(xml);
						} else {							
							this.addFoldersToWebDavModel(sPath, xml);
						}
						this.checkUpdate();
                        return {
                            xml: xml,
                            headers: {
                                total: parseInt(response.headers.get("X-Total-Count"), 10) || 0
                            }
                        };
                    });
                }).catch(error => {
                    console.error("PROPFIND Request Error:", error);
                });
        },
		
		addFoldersToWebDavModel: function(sPath, xml) {
			let oResponseDocument = this.oDomParser.parseFromString(xml, "application/xml");
			let oResponseNodes = oResponseDocument.querySelectorAll("response");
			let oOriginalDocumentNode = this.oData.documentElement;
			for(let oldNode of oResponseNodes) {
				const newNode = this.oData.importNode(oldNode, true);
				oOriginalDocumentNode.appendChild(newNode);
			}
			
		},
		
        bindList: function(sPath, oContext, aSorters, aFilters, mParameters) {
            console.log("bindList called with path:", sPath);

            const binding = new WebdavListBinding(this, sPath, oContext, aSorters, aFilters, mParameters);

            console.log("WebdavListBinding:", binding);

            return binding;
        }
    });
	
	/**
	 * Register function calls that should be called after an update (e.g. calling <code>dataReceived</code> event of a binding)
	 * @param {function} oFunction The callback function
	 * @private
	 */
	WebdavModel.prototype.callAfterUpdate = function(oFunction) {
		this.aCallAfterUpdate.push(oFunction);
	};
	
	/**
	 * Process handlers registered for execution after update.
	 *
	 * @private
	 */
	WebdavModel.prototype._processAfterUpdate = function() {
		var aCallAfterUpdate = this.aCallAfterUpdate;
		this.aCallAfterUpdate = [];
		for (var i = 0; i < aCallAfterUpdate.length; i++) {
			aCallAfterUpdate[i]();
		}
	};
	
	/**
	 * Calls {@link sap.ui.model.Binding#checkUpdate} on all active bindings of this model like
	 * {@link sap.ui.model.Model#checkUpdate}. Additionally, multiple asynchronous calls to this
	 * function lead to a single synchronous call where <code>mChangedEntities</code> is the union
	 * of all <code>mChangedEntities</Code> from the asynchronous calls.
	 *
	 * @param {boolean} [bForceUpdate]
	 *   The parameter <code>bForceUpdate</code> for the <code>checkUpdate</code> call on the
	 *   bindings
	 * @param {boolean} bAsync
	 *   Whether this function is called in a new task via <code>setTimeout</code>
	 * @param {map} mChangedEntities
	 *   Map of changed entities
	 * @param {boolean} bMetaModelOnly
	 *   Whether to only update metamodel bindings
	 * @private
	 */
	WebdavModel.prototype.checkUpdate = function(bForceUpdate) {
		var aBindings = this.getBindings();
		aBindings.forEach(function(oBinding) {
			oBinding.checkUpdate(bForceUpdate);
		}.bind(this));
		this._processAfterUpdate();
	};

    return WebdavModel;
});