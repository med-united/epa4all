sap.ui.define([
	"sap/ui/core/ResizeHandler",
	"medunited/base/controller/AbstractController",
	"sap/f/FlexibleColumnLayout",
	"sap/base/Log",
	"sap/m/Dialog",
	"sap/m/Button",
	"sap/m/library",
	"sap/m/MessageToast",
	"sap/m/Text"
], function (ResizeHandler, AbstractController, FlexibleColumnLayout, Log, Dialog, Button, mobileLibrary, MessageToast, Text) {
	"use strict";

	let ButtonType = mobileLibrary.ButtonType;
	let DialogType = mobileLibrary.DialogType;

	return AbstractController.extend("medunited.care.controller.App", {
		onInit: function () {
			var oComponent = this.getOwnerComponent();
			if (oComponent) {
				this.oRouter = oComponent.getRouter();
				this.oRouter.attachRouteMatched(this.onRouteMatched, this);
				ResizeHandler.register(this.getView().byId("fcl"), this._onResize.bind(this));
			} else {
				Log.warning("Could not get component for AbstractAppController for entity: " + this.getEntityName());
			}

			var sTelematikId = localStorage.getItem("telematikId");
            if (!sTelematikId) {
                this.showSettingsDialog();
            }
		},
		changeTab: function (oEvent) {
			this.getOwnerComponent().getRouter().navTo(oEvent.getParameter("key"));
		},
		onRouteMatched: function (oEvent) {
			var sRouteName = oEvent.getParameter("name"),
				oArguments = oEvent.getParameter("arguments");

			var oModel = this.getOwnerComponent().getModel("Layout");

			var sLayout = oEvent.getParameters().arguments.layout;

			// If there is no layout parameter, query for the default level 0 layout (normally OneColumn)
			if (!sLayout) {
				var oNextUIState = this.getOwnerComponent().getHelper().getNextUIState(0);
				sLayout = oNextUIState.layout;
			}

			// Update the layout of the FlexibleColumnLayout
			if (sLayout) {
				oModel.setProperty("/layout", sLayout);
			}

			this._updateUIElements();

			// Save the current route name
			this.currentRouteName = sRouteName;
			this.entityName = sRouteName.split("-")[0];
			if(this.entityName == "home") {
				this.byId("iconTabHeader").setSelectedKey("patient-master");
			} else {
				this.byId("iconTabHeader").setSelectedKey(this.entityName+"-master");
			}
			this.currentEntity = oArguments[this.entityName];
		},
		onStateChanged: function (oEvent) {
			var bIsNavigationArrow = oEvent.getParameter("isNavigationArrow"),
				sLayout = oEvent.getParameter("layout");

			this._updateUIElements();

			// Replace the URL with the new layout if a navigation arrow was used
			if (bIsNavigationArrow) {
				var oParams = { layout: sLayout };
				oParams[this.entityName] = this.currentEntity;
				this.oRouter.navTo(this.currentRouteName, oParams, true);
			}
		},

		// Update the close/fullscreen buttons visibility
		_updateUIElements: function () {
			var oModel = this.getOwnerComponent().getModel("Layout");
			var oUIState = this.getOwnerComponent().getHelper().getCurrentUIState();
			oModel.setData(oUIState);
		},
		_onResize: function (oEvent) {
			var bPhone = (oEvent.size.width < FlexibleColumnLayout.TABLET_BREAKPOINT);
			this.getOwnerComponent().getModel("Layout").setProperty("/isPhone", bPhone);
		},

		onExit: function () {
			try {
				this.oRouter.detachRouteMatched(this.onRouteMatched, this);
			} catch (e) {
				Log.warning(e);
			}
		},
		dialogToLogOut: function () {
			if (!this.oLogoutDialog) {
				this.oLogoutDialog = new Dialog({
					type: DialogType.Message,
					title: this.translate("logOut"),
					content: new Text({ text: this.translate("doYouWantToLogOutOfTheSystem") }),
					beginButton: new Button({
						type: ButtonType.Emphasized,
						text: this.translate("yes"),
						press: function () {
							MessageToast.show(this.translate("loggingOut"));
							this.oLogoutDialog.close();
							let keycloak = this.getView().getParent().keycloak;
							const logoutOptions = { redirectUri: "https://care.med-united.health" };
							keycloak.logout(logoutOptions).then((success) => {
								console.log("Log out success ", success);
							}).catch((error) => {
								console.log("Log out error ", error);
							});
						}.bind(this)
					}),
					endButton: new Button({
						text: this.translate("cancel"),
						press: function () {
							this.oLogoutDialog.close();
						}.bind(this)
					})
				});
			}
			this.oLogoutDialog.open();
		},
		onAvatarPress: function (oEvent) {
        	var oButton = oEvent.getSource();
        	if (!this._oMenu) {
        		this._oMenu = new sap.m.Menu({
        			items: [
        			    new sap.m.MenuItem({
                            text: this.translate("settings"),
                            press: this.showSettingsDialog.bind(this)
                        }),
        				new sap.m.MenuItem({
        					text: this.translate("logOut"),
        					press: this.dialogToLogOut.bind(this)
        				})
        			]
        		});
        	}
        	this._oMenu.openBy(oButton);
        },
        showSettingsDialog: function () {
          if (!this._oDialog) {
            this._oDialog = sap.ui.xmlview({
              viewName: "medunited.care.view.SettingsDialog",
              type: sap.ui.core.mvc.ViewType.XML
            });
            this.getView().addDependent(this._oDialog);
          }
          this._oDialog.byId("settingsDialog").open();
        }
	});
}, true);