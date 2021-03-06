package cc.blynk.server.hardware.handlers.hardware.logic;

import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.utils.ParseUtil;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Command.SET_WIDGET_PROPERTY;
import static cc.blynk.utils.BlynkByteBufUtil.illegalCommandBody;
import static cc.blynk.utils.BlynkByteBufUtil.ok;
import static cc.blynk.utils.StringUtils.split3;

/**
 * Handler that allows to change widget properties from hardware side.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class SetWidgetPropertyLogic {

    private static final Logger log = LogManager.getLogger(SetWidgetPropertyLogic.class);

    private final SessionDao sessionDao;

    public SetWidgetPropertyLogic(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    public void messageReceived(ChannelHandlerContext ctx, HardwareStateHolder state, StringMessage message) {
        String[] bodyParts = split3(message.body);

        if (bodyParts.length != 3) {
            log.debug("SetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        final String property = bodyParts[1];
        final String propertyValue = bodyParts[2];

        if (property.length() == 0 || propertyValue.length() == 0) {
            log.debug("SetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        DashBoard dash = state.dash;

        if (!dash.isActive) {
            return;
        }

        if (Widget.isNotValidProperty(property)) {
            log.debug("Unsupported set property {}.", property);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        int deviceId = state.device.id;
        byte pin = ParseUtil.parseByte(bodyParts[0]);

        //for now supporting only virtual pins
        Widget widget = dash.findWidgetByPin(deviceId, pin, PinType.VIRTUAL);

        if (widget != null) {
            try {
                widget.setProperty(property, propertyValue);
                dash.updatedAt = System.currentTimeMillis();
            } catch (Exception e) {
                log.debug("Error setting widget property. Reason : {}", e.getMessage());
                ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
                return;
            }
        } else {
            //this is possible case for device selector
            dash.putPinPropertyStorageValue(deviceId, PinType.VIRTUAL, pin, property, propertyValue);
        }

        Session session = sessionDao.userSession.get(state.userKey);
        session.sendToApps(SET_WIDGET_PROPERTY, message.id, dash.id, deviceId, message.body);
        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
