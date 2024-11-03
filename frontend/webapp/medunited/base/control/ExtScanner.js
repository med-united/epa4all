/* global ZXing:true */
sap.ui.define(
  [
    'sap/ui/core/Control',
    'jquery.sap.global',
    'sap/ui/model/json/JSONModel',
    'sap/m/MessageToast',
    'sap/m/ResponsivePopover',
    'sap/m/List',
    'sap/m/DisplayListItem',
    'sap/ui/Device',
  ],
  function (Control, jQuery, JSONModel, MessageToast, ResponsivePopover, List, DisplayListItem, Device) {
    'use strict';

    return Control.extend('medunited.base.control.ExtScanner', {
      metadata: {
        manifest: 'json',
        properties: {
          // possible 'QR-Code', 'Barcode', 'DatamatrixCode', 'Multi'
          type: {
            type: 'string',
            defaultValue: 'Multi',
          },
          editMode: {
            type: 'boolean',
            defaultValue: true,
          },
          settings: {
            type: 'boolean',
            defaultValue: false,
          },
          tryHarder: {
            type: 'boolean',
            defaultValue: false,
          },
          laser: {
            type: 'boolean',
            defaultValue: false,
          },
        },
        events: {
          valueScanned: {
            parameters: {
              value: {
                type: 'string',
              },
            },
          },
          cancelled: {},
        },
      },

      init: function () {
        // set initial properties
        this._oToolbar = null;
        this._oScanModel = null;
        this._oTD = null; // scan dialog
        this._oID = null; // input dialog
        this._oBarCodeDecoder = null;
        this._oQRCodeDecoder = null;
        this._oDatamatrixCodeDecoder = null;

        // set main model
        this._oScanModel = new JSONModel({
          type: this.getType(),
          editButton: this.getEditMode(),
          changeButton: true,
          value: '',
          videoDeviceId: null,
          tryHarder: this.getTryHarder(),
          settings: this.getSettings(),
        });

        // i18n from owner or library if possible
        this.setModel(sap.ui.core.Component.getOwnerComponentFor(this).getModel('i18n'), 'i18n');
        this.setModel(this._oScanModel, 'scanModel');
        this.setModel(new JSONModel(Device), 'device');
        // attach
        sap.ui.Device.orientation.attachHandler(this.adaptVideoSourceSize.bind(this));
      },

      adaptByOrientationChange: function () {
        this.adaptVideoSourceSize();
        this._resetScan();
      },

      exit: function () {
        sap.ui.Device.orientation.detachHandler(this.adaptVideoSourceSize);
      },

      setSettings: function (bValue) {
        if (this._oScanModel) {
          this._oScanModel.setProperty('/settings', bValue);
        }
        this.setProperty('settings', bValue);
      },

      setTryHarder: function (bHarder) {
        this.setProperty('tryHarder', bHarder, false);
        if (this._oDecoder) {
          this.resetDecoder();
        }
      },


      /**
       * Opens the dialog for scanning a zxing.
       * @public
       */
      open: function () {
        this.onShowDialog();
      },

      setType: function (sType) {
        if (sType === 'Barcode' || sType === 'QR-Code' || sType === 'DatamatrixCode' || sType === 'Multi') {
          this.setProperty('type', sType);
          if (this._oScanModel) {
            this._oScanModel.setProperty('/type', sType);
          }
        }
      },


      onHarderChange: function () {
        let bHarder = this._oScanModel.getProperty('/tryHarder');
        let bOldHarder = this.getProperty('tryHarder');
        if (bOldHarder !== bHarder) {
          this.setTryHarder(bHarder);
        }
        if (this.oSettingsPopover) {
          this.oSettingsPopover.close();
        }
      },

      onSettingsPopover: function (oEvent) {
        let oSource = oEvent.getSource();
        this.getSettingsPopover().openBy(oSource);
      },

      getSettingsPopover: function () {
        if (!this.oSettingsPopover) {
          this.oSettingsPopover = sap.ui.xmlfragment(this.getId(), 'medunited.base.control.fragments.settingsPopover', this);
          this._getScanDialog().addDependent(this.oSettingsPopover);
        }
        return this.oSettingsPopover;
      },

      // TODO: refactor
      setEditMode: function (bEditMode) {
        let sOld = this.getProperty('editMode');
        this.setProperty('editMode', bEditMode);
        if (this._oScanModel) {
          this._oScanModel.setProperty('/editButton', bEditMode);
          if (sOld !== bEditMode) {
            let oTD = this._getScanDialog();
            if (bEditMode === true) {
              this._addHeader(oTD);
            } else {
              oTD.setCustomHeader();
            }
          }
        }
      },

      onChangePress: function (oEvent) {
        let aDevices = this._oScanModel.getProperty('/videoDevices');
        if (aDevices && aDevices.length > 1) {
          if (aDevices.length === 2) {
            let sCurrent = this.getCurrentVideoDevice();
            aDevices.some(
              function (item) {
                if (item.deviceId !== sCurrent) {
                  this.changeDevice(item.deviceId);
                  return true;
                }
                return false;
              }.bind(this)
            );
          } else {
            this.openChangePopover(oEvent.getSource());
          }
        }
      },

      onChangeDevice: function (oEvent) {
        let oItem = oEvent.getProperty('listItem');
        let sId = oItem.getValue();
        this.getChangePopover().close();
        this.changeDevice(sId);
      },

      changeDevice: function (sId) {
        this._oScanModel.setProperty('/videoDeviceId', sId);
        this._stopScan();
        this._startScan();
      },

      getChangePopover: function () {
        if (!this._oChangePopover) {
          let oList = new List({
            mode: 'SingleSelectMaster',
          });
          oList.setModel(this._oScanModel);
          oList.bindItems({
            path: '/videoDevices',
            template: new DisplayListItem({
              value: '{label}',
            }),
          });
          this._oChangePopover = new ResponsivePopover({
            class: 'sapUiContentPadding',
            showHeader: false,
            selectionChange: this.onChangeDevice.bind(this),
          });
          this._oChangePopover.addContent(oList);
        }
        return this._oChangePopover;
      },

      openChangePopover: function (oSource) {
        let oPopover = this.getChangePopover();
        let oList = oPopover.getContent()[0];
        if (oList) {
          let sSelectedId = this.getCurrentVideoDevice();
          if (sSelectedId) {
            let oSelectedItem = this._oScanModel.getProperty('/videoDevices').find(function (item) {
              return item.deviceId === sSelectedId;
            });
            let oItem = oList.getItems().find(function (item) {
              return item.getValue() === oSelectedItem.label;
            });
            if (oItem) {
              oList.setSelectedItem(oItem);
            }
          } else {
            oList.removeSelections(true);
          }
        }
        oPopover.openBy(oSource, false);
      },

      getCurrentVideoDevice: function () {
        let sId = this._oScanModel.getProperty('/videoDeviceId');
        if (!sId) {
          if (this._oDecoder && this._oDecoder.stream) {
            let oTrack = this._oDecoder.stream.getVideoTracks()[0];
            if (oTrack) {
              let oCap = oTrack.getCapabilities ? oTrack.getCapabilities() : oTrack.getSettings();
              if (oCap) {
                sId = oCap.deviceId;
              }
            }
          }
        }
        return sId;
      },

      resetDecoder: function () {
        this._oScanModel.setProperty('/value', '');
        this.lastScannedResult = null;
        this._stopScan();
        this._oBarCodeDecoder = null;
        this._oQRCodeDecoder = null;
        this._oMultiCodeDecoder = null;
        this.initDecoder();
        this._startScan();
      },

      initDecoder: function () {
        let sType = this.getProperty('type');
        switch (sType) {
          case 'Barcode':
            this._oDecoder = this._getBarCodeDecoder();
            break;
          case 'QR-Code':
            this._oDecoder = this._getQRCodeDecoder();
            break;
          case 'DatamatrixCode':
            this._oDecoder = this._getDatamatrixCodeDecoder();
            break;
          default:
            this._oDecoder = this._getMultiCodeDecoder();
        }
        return this._oDecoder;
      },

      onShowDialog: function () {
        this.lastScannedResult = null;
        this.initDecoder();
        if (this._oDecoder) {
          this._oDecoder
            .listVideoInputDevices()
            .then(
              function (aDevices) {
                this._oScanModel.setProperty('/videoDevices', aDevices);
                if (aDevices && aDevices.length) {
                  if (aDevices.length === 1) {
                    this._oScanModel.setProperty('/changeButton', false);
                  } else {
                    this._oScanModel.setProperty('/changeButton', true);
                  }
                } else {
                  throw new Error('No video devices');
                }
                return true;
              }.bind(this)
            )
            .then(
              function () {
                this._showScanDialog();
              }.bind(this)
            )
            .catch(
              function (error) {
                jQuery.sap.log.warning(error);
                this._showInputDialog();
              }.bind(this)
            );
        }
      },

      onCancelPress: function (oEvent) {
        let oDialog = oEvent.getSource().getParent();
        oDialog.close();
        this.fireCancelled({});
        this._oScanModel.setProperty('/value', '');
      },

      onOkPress: function (oEvent) {
        let oDialog = oEvent.getSource().getParent();
        oDialog.close();
        this.fireValueScanned({
          value: this._oScanModel.getProperty('/value'),
        });
        this._oScanModel.setProperty('/value', '');
      },

      onChangeMedicationPlanXml: function (oEvent) {
        let oDialog = oEvent.getSource().getParent();
        oDialog.close();
        this.fireValueScanned({
          value: oEvent.getParameter("value"),
        });
        oEvent.getSource().setValue("");
      },

      _getOpenButton: function () {
        if (!this._oBtn) {
          this._oBtn = new Button(this.createId('idScanOpenDialogBtn'), {
            icon: 'sap-icon://bar-code',
            press: this.onShowDialog.bind(this),
          });
        }
        return this._oBtn;
      },

      _getBarCodeDecoder: function () {
        if (!this._oBarCodeDecoder) {
          this._oBarCodeDecoder = new ZXing.BrowserBarcodeReader(new Map([['TRY_HARDER', this.getTryHarder()]]));
        }
        return this._oBarCodeDecoder;
      },

      _getQRCodeDecoder: function () {
        if (!this._oQRCodeDecoder) {
          this._oQRCodeDecoder = new ZXing.BrowserQRCodeReader(new Map([['TRY_HARDER', this.getTryHarder()]]));
        }
        return this._oQRCodeDecoder;
      },

      _getDatamatrixCodeDecoder: function () {
        if (!this._oDatamatrixCodeDecoder) {
          this._oDatamatrixCodeDecoder = new ZXing.BrowserDatamatrixCodeReader(new Map([['TRY_HARDER', this.getTryHarder()]]));
        }
        return this._oDatamatrixCodeDecoder;
      },

      _getMultiCodeDecoder: function () {
        if (!this._oMultiCodeDecoder) {
          this._oMultiCodeDecoder = new ZXing.BrowserMultiFormatReader(new Map([['TRY_HARDER', this.getTryHarder()]]));
        }
        return this._oMultiCodeDecoder;
      },

      _showInputDialog: function () {
        this._openDialog(this._getInputDialog());
      },

      _showScanDialog: function () {
        this._openDialog(this._getScanDialog());
        this.updateLaser();
        this.adaptVideoSourceSize();
      },

      adaptVideoSourceSize: function () {
        let oDevice = this.getModel('device');
        let bPhone = oDevice.getProperty('/system/phone');
        let bPortain = oDevice.getProperty('/orientation/portrait');
        let iWidth = oDevice.getProperty('/resize/width');
        let iHeight = oDevice.getProperty('/resize/height');
        var tmp;
        if (bPhone) {
          if (bPortain) {
            iHeight -= 96;
          } else {
            tmp = iHeight;
            iHeight = iWidth - 96;
            iWidth = tmp;
          }
        }
        if (bPhone) {
          $('div[id$="videoContainer"]').width(iWidth);
          $('div[id$="videoContainer"]').height(iHeight);
        } else {
          $('div[id$="videoContainer"]').width('100%');
          $('div[id$="videoContainer"]').height('100%');
        }
      },

      updateLaser: function () {
        let bLaser = this.getProperty('laser');
        if (bLaser) {
          $('.scanner-laser').show();
        } else {
          $('.scanner-laser').hide();
        }
      },

      _openDialog: function (oDialog) {
        if (this.getModel('device').getProperty('/system/phone') === true) {
          oDialog.setStretch(true);
        } else {
          oDialog.setStretch(false);
        }
        oDialog.open();
      },

      _getScanDialog: function () {
        if (!this._oTD) {
          this._oTD = sap.ui.xmlfragment(this.getId(), 'medunited.base.control.fragments.scanDialog', this);
          if (this.getEditMode() === true) {
            this._addHeader(this._oTD);
          }
          this._oTD.attachAfterOpen(this._onAfterOpen.bind(this));
          this._oTD.attachAfterClose(this._onAfterClose.bind(this));
          this.addDependent(this._oTD);
        }
        return this._oTD;
      },

      _getInputDialog: function () {
        if (!this._oID) {
          this._oID = sap.ui.xmlfragment(this.getId(), 'medunited.base.control.fragments.inputDialog', this);
          this.addDependent(this._oID);
        }
        return this._oID;
      },

      _addHeader: function (oDialog) {
        if (oDialog) {
          let oHeader = this._getDialogHeader();
          oDialog.setCustomHeader(oHeader);
          oDialog.invalidate();
        }
      },

      _startScan: function () {
        this._oDecoder
          .decodeFromVideoDevice(this._oScanModel.getProperty('/videoDeviceId'), this.getId() + '--scanVideo', this._saveScannedValue.bind(this))
          .catch(
            function (err) {
              if (err && err.name && err.name === 'NotAllowedError') {
                MessageToast.show('Video device is blocked');
              } else {
                MessageToast.show(err.message || 'Unexpected error in decoder, switch to input mode');
              }
              this._getScanDialog().close();
              this._showInputDialog();
              jQuery.sap.log.error(err);
            }.bind(this)
          );
      },

      _stopScan: function () {
        this._oDecoder.stopContinuousDecode();
        this._oDecoder.stopAsyncDecode();
        this._oDecoder.reset();
      },

      _onAfterOpen: function () {
        this._startScan();
      },

      _onAfterClose: function () {
        this._stopScan();
      },

      _resetScan: function () {
        this._stopScan();
        this._startScan();
      },

      onResetScan: function () {
        this._oScanModel.setProperty('/value', '');
        this.lastScannedResult = null;
        this._resetScan();
        if (this.oSettingsPopover) {
          this.oSettingsPopover.close();
        }
      },


      _saveScannedValue: function (result, error) {
        if (result) {
          if (this.lastScannedResult !== null) {
            return
          }
          this.lastScannedResult = result;
          let sResultText = result.text;
          this._oScanModel.setProperty('/value', sResultText);
          if (this.getEditMode() === false) {
            this.fireValueScanned({
              value: sResultText,
            });
            this._getScanDialog().close();
          } else {
            MessageToast.show(sResultText);
          }
        }
        if (error && !(error instanceof ZXing.NotFoundException)) {
          jQuery.sap.log.warning('Error by decode from video stream (Multi)');
          jQuery.sap.log.warning(error);
        }
      },

      _getDialogHeader: function () {
        if (!this._oHeader) {
          this._oHeader = sap.ui.xmlfragment(this.getId(), 'medunited.base.control.fragments.toolbar', this);
          this._getScanDialog().addDependent(this._oHeader);
        }
        return this._oHeader;
      },

      getTitle: function (sText, sMode) {
        return sMode === 'Multi' ? sText + ' Multiple formats' : sText + ' ' + sMode;
      },
    });
  }
);