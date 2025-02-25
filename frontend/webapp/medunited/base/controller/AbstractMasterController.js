sap.ui.define([
    "./AbstractController",
    "sap/ui/model/Filter",
    "sap/ui/model/FilterType",
    "sap/ui/model/FilterOperator",
    'sap/ui/model/Sorter',
    'sap/m/MessageBox',
    'sap/m/MessageToast',
    "sap/ui/core/Fragment"
], function (AbstractController, Filter, FilterType, FilterOperator, Sorter, MessageBox, MessageToast, Fragment) {
    "use strict";

    return AbstractController.extend("medunited.base.controller.AbstractMasterController", {
        onInit: function () {
            this.oRouter = this.getOwnerComponent().getRouter();

            this.oRouter.attachRouteMatched(function (oEvent) {
                if (oEvent.getParameter("name") == this.getEntityName().toLowerCase() + "-add") {
                    this.onRouteAddMatched(oEvent);
                }
            }.bind(this));

            this.oRouter.attachRouteMatched(function (oEvent) {
                if (oEvent.getParameter("name") == this.getEntityName().toLowerCase() + "-search") {
                    this.onRouteSearchMatched(oEvent);
                }
            }.bind(this));

            this._bDescendingSort = false;
        },
        onRouteSearchMatched: function (oEvent) {
            let sQuery = oEvent.getParameter("arguments").query;

            let aQueryParts = sQuery.split(/&/).map(s => s.split(/=/)).map(a => [a[0].replace("_", "/"), a[1]]);

            let aFilters = [];
            for (let aPart of aQueryParts) {
                let sField = aPart[0];
                let sValue = aPart[1];
                aFilters.push(new Filter({ path: sField, operator: FilterOperator.EQ, value1: sValue }));
            }
            this.byId(this.getEntityName().toLowerCase() + "Table").getBinding("items").filter(aFilters, FilterType.Application);
        },
        onListItemPress: function (oEvent) {
            let oNextUIState = this.getOwnerComponent().getHelper().getNextUIState(1),
                entityPath = oEvent.getSource().getBindingContext().getPath(),
                entity = entityPath.split("/").slice(-1).pop();

            let oParams = { layout: oNextUIState.layout };
            oParams[this.getEntityName().toLowerCase()] = entity;
            this.oRouter.navTo(this.getEntityName().toLowerCase() + "-detail", oParams);
        },
        onSearch: function (oEvent) {
            let oTableSearchState = [],
                sQuery = oEvent.getParameter("query");

            if (sQuery && sQuery.length > 0) {
                oTableSearchState = this.getFilter(sQuery);
            }
            this.getView().byId(this.getEntityName().toLowerCase() + "Table").getBinding("items").filter(oTableSearchState, "Application");
        },
        getFilter: function (sQuery) {
            return [new Filter(this.getSearchField(), FilterOperator.Contains, sQuery)];
        },
        onAdd: function (oEvent) {
            this.oRouter.navTo(this.getEntityName().toLowerCase() + "-add");
        },
        onRouteAddMatched: function (oEvent) {
            let oView = this.getView();
            const me = this;
            // create dialog lazily
            if (!this.byId("createDialog")) {
                // load asynchronous XML fragment
                Fragment.load({
                    id: oView.getId(),
                    name: "medunited.care.view." + this.getEntityName().toLowerCase() + ".CreateDialog",
                    controller: this
                }).then(function (oDialog) {
                    me.onAfterCreateOpenDialog({ "dialog": oDialog });
                    // connect dialog to the root view of this component (models, lifecycle)
                    oView.addDependent(oDialog);
                    this._openCreateDialog(oDialog);
                }.bind(this));
            } else {
                this._openCreateDialog(this.byId("createDialog"));
            }
        },
        onAfterCreateOpenDialog: function () {

        },
        getPackageName: function () {
            return this.getEntityName().toLowerCase();
        },
        getEntityName: function () {
            throw new Error("getEntityName must be implemented by derived class");
        },
        _openCreateDialog: function (oDialog, sEntityName) {
            oDialog.open();
        },
        save: function () {
            const fnSuccess = (oData) => {
                MessageToast.show(this.translate(this.getEntityName()) + ' ' + this.translate("msgSaveResourceSuccessful"));
            };

            const fnError = (oError) => {
                MessageBox.show(this.translate(this.getEntityName()) + ' ' + this.translate("msgSaveResouceFailed", [oError.statusCode, oError.statusText]));
            };

            this.getView().getModel().submitChanges(this.getEntityName().toLowerCase() + "Details", fnSuccess, fnError);
        },
        onSave: function (oEvent) {
            this.save();
            this.byId("createDialog").close();
            this.oRouter = this.getOwnerComponent().getRouter();
            this.oRouter.navTo(this.getEntityName().toLowerCase() + "-master");
        },
        onCancel: function (oEvent) {
            // this.getOwnerComponent().getModel().resetChanges();
            oEvent.getSource().getParent().close();
            this.oRouter = this.getOwnerComponent().getRouter();
            this.oRouter.navTo(this.getEntityName().toLowerCase() + "-master");
        },
        onSort: function (oEvent) {
            this._bDescendingSort = !this._bDescendingSort;
            this.sort();
        },
        sort: function () {
            let oView = this.getView(),
                oTable = oView.byId(this.getEntityName().toLowerCase() + "Table"),
                oBinding = oTable.getBinding("items"),
                oSorter = new Sorter(this.getSortField(), this._bDescendingSort);

            oBinding.sort(oSorter);
            oBinding.refresh();
        },
        getSearchField: function () {
            return this.getSortField();
        },
        getSortField: function () {
            return "Name";
        },
        onDeleteSelected: function () {
            const aResources = this.byId(this.getEntityName().toLowerCase() + "Table").getSelectedItems().map(oItem => oItem.getBindingContext().getPath());
            const iCount = aResources.length;
            const oModel = this.getView().getModel();
            const me = this;
            const sPageId = this.getEntityName().toLowerCase() + "PageId";
            const oPage = this.byId(sPageId);
            oModel.remove(aResources);
            oPage.setShowFooter();

            oModel.submitChanges(function () {
                MessageToast.show(me.translate("msgCountDeleted", iCount));
            }, function (oError) {
                MessageBox.show(me.translate("msgSavedFailed", [oError.statusCode, oError.statusText]));
            });
        },
        onSelectionChange: function (oEvent) {
            const bShow = oEvent.getSource().getSelectedItems(true).length > 0;
            const sPageId = this.getEntityName().toLowerCase() + "PageId";
            const oPage = this.byId(sPageId);
            if (oPage.getShowFooter() !== bShow) {
                oPage.setShowFooter(bShow);
            }
        }
    });
}, true);