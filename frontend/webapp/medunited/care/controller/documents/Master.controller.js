sap.ui.define([
    'medunited/base/controller/AbstractMasterController',
    'sap/ui/model/json/JSONModel',
    'sap/m/MessageToast'
], function (AbstractMasterController, JSONModel, MessageToast) {
    "use strict";

    return AbstractMasterController.extend("medunited.care.controller.documents.Master", {

        onInit: function () {
            var oModel = new JSONModel({ results: [] });
            this.getView().setModel(oModel, "documents");
        },

        onSearchDocuments: function (oEvent) {
            var sQuery = oEvent.getParameter("query");
            if (!sQuery) {
                this.getView().getModel("documents").setProperty("/results", []);
                return;
            }

            sap.ui.core.BusyIndicator.show(0);

            var sSearchXML = `<d:searchrequest xmlns:d="DAV:">
                <d:JCR-SQL2><![CDATA[
                    SELECT * FROM [nt:resource] AS r WHERE CONTAINS(r.*, '${sQuery}')
                ]]></d:JCR-SQL2>
            </d:searchrequest>`;

            let sTelematikId = localStorage.getItem("telematikId");
            if (!sTelematikId) {
                MessageToast.show("Telematik ID is missing");
                return;
            }

            let sUrl = `http://localhost:8090/webdav2/${sTelematikId}/jcr:root/rootFolder`;

            var that = this;
            $.ajax({
                url: sUrl,
                method: "SEARCH",
                data: sSearchXML,
                contentType: "text/xml",
                processData: false,
                success: function (data, textStatus, jqXHR) {
                    sap.ui.core.BusyIndicator.hide();

                    var xmlDoc = jqXHR.responseXML;
                    if (!xmlDoc) {
                        that.getView().getModel("documents").setProperty("/results", []);
                        return;
                    }

                    var aResults = [];
                    $(xmlDoc).find("D\\:response, response").each(function () {
                        var sHref = $(this).find("D\\:href, href").text();
                        var sPath = $(this).find("dcr\\:column dcr\\:name:contains('jcr:path')").siblings("dcr\\:value").text();
                        var sEncoding = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:encoding')").siblings("dcr\\:value").text();                        var sLastModified = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:lastModified')").siblings("dcr\\:value").text();
                        var sMimeType = $(this).find("dcr\\:column dcr\\:name:contains('r.jcr:mimeType')").siblings("dcr\\:value").text();

                        var sDocumentUrl = sHref.replace("jcr%3acontent/", "");

                        aResults.push({
                            path: sPath,
                            url: sDocumentUrl,
                            encoding: sEncoding,
                            lastModified: sLastModified,
                            mimeType: sMimeType,
                        });
                    });

                    that.getView().getModel("documents").setProperty("/results", aResults);
                },
                error: function (oError) {
                    sap.ui.core.BusyIndicator.hide();
                    console.error("SEARCH request failed:", oError);
                    MessageToast.show("Search failed. Please check server logs.");
                }
            });
        }
    });
});
