sap.ui.define([
    "medunited/base/controller/AbstractController",
    "../../utils/Formatter",
    "sap/fhir/model/r4/FHIRFilter",
    "sap/fhir/model/r4/FHIRFilterType",
    "sap/fhir/model/r4/FHIRFilterOperator",
    "sap/ui/core/Item",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "sap/m/ColumnListItem",
	"sap/ui/model/ChangeReason",
	'sap/ui/model/json/JSONModel',
], function (AbstractController, Formatter, FHIRFilter, FHIRFilterType, FHIRFilterOperator, Item, MessageToast, MessageBox, ColumnListItem, ChangeReason, JSONModel) {
    "use strict";

    return AbstractController.extend("medunited.care.SharedBlocks.document.DocumentBlockController", {

        formatter: Formatter,

        onInit: function () {
            this.initializeRouter();

            var oModel = new JSONModel({ results: [] });
            this.getView().setModel(oModel, "documents");
        },

        initializeRouter: function () {
            this.oRouter = sap.ui.core.UIComponent.getRouterFor(this);
            this.oRouter.getRoute("patient-detail").attachPatternMatched(this.onPatientRouteMatched, this);
        },
		onDocumentItemPress: function (oEvent) {
			const oContext = this.getView().getBindingContext();
			const sPatientId = oContext.getProperty("propstat/prop/displayname");
			const sPatientPath = oContext.getPath();
			const sLastId = sPatientPath.split(/\//).pop();
			this.oRouter.navTo("patient-detail", {
				"patient" : sLastId,
				"layout": "ThreeColumnsEndExpanded",
				"document": encodeURIComponent(oEvent.getSource().getBindingContext().getProperty("href"))
			});
		},
		onSearchDocumentItemPress: function (oEvent) {
            const oItemContext = oEvent.getSource().getBindingContext("documents");
            const sWebdavPath = oItemContext.getProperty("href");
            const sMimeType = oItemContext.getProperty("mimeType");

            const oContext = this.getView().getBindingContext();
            const sPatientPath = oContext?.getPath();
            const sLastId = sPatientPath?.split(/\//).pop();

            console.log("Opening document:", sWebdavPath);

            if (sWebdavPath && sLastId) {
                const sWebdavPath = oItemContext.getProperty("href");

                this.oRouter.navTo("patient-detail", {
                  patient: sLastId,
                  layout: "ThreeColumnsEndExpanded",
                  document: encodeURIComponent(sWebdavPath)
                });
            } else {
                MessageToast.show("Document URL or patient ID missing");
            }
        },
        onPatientRouteMatched: function (oEvent) {
            this._entity = oEvent.getParameter("arguments").patient;
        },
		addDocument: function () {
                        
        },
        deleteDocument: function () {
            
        },
        onSuggestDocumentName: function (oEvent) {
            
        },
        onSearchDocuments: function (oEvent) {
            const sQuery = oEvent.getParameter("query");

            const oContext = this.getView().getBindingContext();
            const sPatientId = oContext?.getProperty("propstat/prop/displayname");

            const oTable = this.byId("documentTable");

            let oDocumentsModel = this.getView().getModel("documents");
            if (!oDocumentsModel) {
                oDocumentsModel = new sap.ui.model.json.JSONModel({ results: [] });
                this.getView().setModel(oDocumentsModel, "documents");
            }

            if (!sQuery) {
                oDocumentsModel.setProperty("/results", []);
                if (oTable) oTable.setVisible(false);
                return;
            }

            if (!sPatientId) {
                MessageToast.show("Patient ID missing");
                return;
            }

            sap.ui.core.BusyIndicator.show(0);

            const sSearchXML = `<d:searchrequest xmlns:d="DAV:">
                <d:JCR-SQL2><![CDATA[
                    SELECT * FROM [nt:resource] AS r
                    WHERE CONTAINS(r.*, '${sQuery}')
                ]]></d:JCR-SQL2>
            </d:searchrequest>`;

            const sTelematikId = localStorage.getItem("telematikId");
            if (!sTelematikId) {
                sap.ui.core.BusyIndicator.hide();
                MessageToast.show("Telematik ID is missing");
                return;
            }

            const sUrl = `/webdav2/${sTelematikId}/jcr:root/rootFolder`;
            const that = this;

            $.ajax({
                url: sUrl,
                method: "SEARCH",
                data: sSearchXML,
                contentType: "text/xml",
                processData: false,
                success: function (data, textStatus, jqXHR) {
                    sap.ui.core.BusyIndicator.hide();

                    const xmlDoc = jqXHR.responseXML;
                    if (!xmlDoc) {
                        oDocumentsModel.setProperty("/results", []);
                        if (oTable) oTable.setVisible(false);
                        return;
                    }

                    const aResults = [];
                    $(xmlDoc).find("D\\:response, response").each(function () {
                        const sHref = $(this).find("D\\:href, href").text();
                        const sPath = $(this).find("dcr\\:column dcr\\:name:contains('jcr:path')").siblings("dcr\\:value").text();
                        const sEncoding = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:encoding')").siblings("dcr\\:value").text();
                        const sLastModified = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:lastModified')").siblings("dcr\\:value").text();
                        const sMimeType = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:mimeType')").siblings("dcr\\:value").text();

                        const sDecodedHref = decodeURIComponent(sHref);

                        const sWebdavPath = decodeURIComponent(sHref)
                          .replace(/^.*\/webdav2\//, "/webdav/")
                          .replace(/jcr:root\/rootFolder\//, "")
                          .replace(/\/jcr:content\/?$/, "");

                        aResults.push({
                          path: sPath,
                          href: sWebdavPath,
                          encoding: sEncoding,
                          lastModified: sLastModified,
                          mimeType: sMimeType
                        });
                    });

                    const aFiltered = aResults.filter(doc => doc.path.includes(`/${sPatientId}/`));
                    oDocumentsModel.setProperty("/results", aFiltered);
                    if (oTable) oTable.setVisible(aFiltered.length > 0);
                },
                error: function (oError) {
                    sap.ui.core.BusyIndicator.hide();
                    console.error("SEARCH request failed:", oError);
                    MessageToast.show("Search failed. Please check server logs.");
                    oDocumentsModel.setProperty("/results", []);
                    if (oTable) oTable.setVisible(false);
                }
            });
        }
    });
});