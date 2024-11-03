/* global DataMatrix:true */
sap.ui.define(
    [
        'sap/ui/core/Control',
        'jquery.sap.global'
    ],
    function (Control) {
        'use strict';

        return Control.extend('medunited.base.control.DataMatrixCode', {
            metadata: {
                properties: {
                    // msg - Data Matrix message, obviously, this is mandatory parameter.
                    msg: {
                        type: 'string',
                        defaultValue: '',
                    },
                    // dim - is equal to needed dimention (height) in pixels, default value is 256.
                    dim: {
                        type: 'int',
                        defaultValue: 256,
                    },
                    // rct - set 1 to render rectangle Data Matrix if possible, default value is 0.
                    rct: {
                        type: 'int',
                        defaultValue: 0,
                    },
                    // pad - white space padding, default value is 2 blocks, set 0 for no padding.
                    pad: {
                        type: 'int',
                        defaultValue: 2,
                    },
                    // needed for create array pal - is array of [color,background-color] strings that represent hex color codes, default value is ['#000'] along with transparent background. Set ['#000','#fff'] to make background opaque.
                    color: {
                        type: 'string',
                        defaultValue: '#000000'
                    },
                    // needed for create array pal - is array of [color,background-color] strings that represent hex color codes, default value is ['#000'] along with transparent background. Set ['#000','#fff'] to make background opaque.
                    backgroundColor: {
                        type: 'string',
                        defaultValue: '#ffffff'
                    },
                    vrb: {
                        type: 'int',
                        defaultValue: 0
                    }
                }
            },
            renderer: function (oRm, oControl) {
                
                const sSVG = oControl.getSVGXml();
                oRm.write("<span");
                oRm.writeControlData(oControl);
                oRm.write(">");
                
                oRm.write(sSVG);
                oRm.write("</span>");
            },
            getSVGXml: function () {
                const oSvgNode = DATAMatrix({
                    msg: this.getMsg(),
                    dim: this.getDim(),
                    rct: this.getRct(),
                    pad: this.getPad(),
                    pal: [this.getColor(), this.getBackgroundColor()],
                    vrb: 0
                });
                return oSvgNode.outerHTML;
            }
        });
    });