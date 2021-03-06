//#extension GL_OES_EGL_image_external : require
//precision mediump float;
//uniform samplerExternalOES inputImageTexture;

// orig
varying highp vec2 textureCoordinate;
uniform sampler2D inputImageTexture;

float microAdjust(float yuv, float threshold) {
    if (yuv <= threshold) {
        return yuv;
    }
    float tmp = yuv - threshold;
    tmp = tmp * 1.1 + 0.5;
    return tmp + threshold;
}

void main()
{

     highp vec4 base = texture2D(inputImageTexture, textureCoordinate);

     mediump float curR = base.r;
     mediump float curG = base.g;
     mediump float curB = base.b;
     curR  *= 255.0;
     curG  *= 255.0;
     curB  *= 255.0;
     mediump float curY = 0.257 * curR + 0.504 * curG + 0.098 * curB+16.0;
     mediump float curU =  - 0.148 * curR - 0.291 * curG + 0.439 * curB+128.0;
     mediump float curV = 0.439 * curR - 0.368 * curG - 0.071 * curB+128.0;
     curY = min(max(curY, 0.0), 255.0);
     curU = min(max(curU, 0.0), 255.0);
     curV = min(max(curV, 0.0), 255.0);

     lowp float r = base.r;
     lowp float g = base.g;
     lowp float b = base.b;

//     if (textureCoordinate.x < 0.3) {
//        gl_FragColor = vec4(r, g, b, 0.0);
//        return;
//     } else {
//        gl_FragColor = vec4(r, g, b, 1.0);
//        return;
//     }

     if (r > 0.3725 && g > 0.1568 && b > 0.0784 && r > b && (max(max(r, g), b) - min(min(r, g), b)) > 0.0588 && abs(r - g) > 0.0588) {
         mediump float mXStep = 1.0/1280.0;
         mediump float mYStep = 1.0/720.0;
         mediump float mW = textureCoordinate.x;
         mediump float mH = textureCoordinate.y;
         mediump float radius = 3.0;
         mediump float smoothXishu = 0.2157;
         mediump float smoothXishuEdge = 0.3235;
         curY /= 255.0;
         mediump int pixCountR;
         mediump int pixSave;
         mediump float fenziY1 = 0.0;
         mediump float fenmuY1 = 0.0;
         mediump float fenziY2 = 0.0;
         mediump float fenmuY2 = 0.0;
         mediump float similarTh = 5.0/255.0;
         mediump float a = 0.0;
         mediump vec2 mTex = vec2(mW - mXStep * 10.0, mH - mYStep * 10.0);
         mediump vec4 mBase = texture2D(inputImageTexture, mTex);
         mediump float mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW - 0.5 * mXStep * 10.0, mH - 0.5 * mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW, mH - mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW, mH - 0.5 * mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + mXStep * 10.0, mH - mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + 0.5 * mXStep * 10.0, mH - 0.5 * mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW - mXStep * 10.0, mH);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW - 0.5 * mXStep * 10.0, mH);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + mXStep * 10.0, mH);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + 0.5 * mXStep * 10.0, mH);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW - mXStep * 10.0, mH + mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW - 0.5 * mXStep * 10.0, mH + 0.5 * mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW, mH + mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW, mH + mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + mXStep * 10.0, mH + mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         mTex = vec2(mW + 0.5 * mXStep * 10.0, mH + 0.5 * mYStep * 10.0);
         mBase = texture2D(inputImageTexture, mTex);
         mBaseY = 0.257 * mBase.r + 0.504 * mBase.g + 0.098 * mBase.b+16.0/255.0;
         a = abs(mBaseY - curY);
         pixCountR ++;
         if(a < similarTh) { pixSave ++; }
         if(a < smoothXishu) {
             fenziY1 += (smoothXishu - a) * mBaseY;
             fenmuY1 += (smoothXishu - a);
         }
         if(a < smoothXishuEdge) {
             fenziY2 += (smoothXishuEdge - a) * mBaseY;
             fenmuY2 += (smoothXishuEdge - a);
         }
         if((pixSave  *  5) > (pixCountR * 4)) {
             if(fenmuY1 > 0.0) {
                 mediump float tempY = fenziY1 / fenmuY1;
                 tempY = min(max(tempY,0.0),1.0);
                 curY = curY - (curY - tempY) * smoothXishu/(100.0/255.0);
             }
         }
         else {
             if(fenmuY2 > 0.0) {
                 mediump float tempY = fenziY2/fenmuY2;
                 tempY = min(max(tempY,0.0),1.0);
                 curY = curY - (curY - tempY) * smoothXishu/(100.0/255.0);
             }
         }

        curY *= 255.0;
     }

     // 这是orig，但是可能会产生负数
//     mediump float tempY;
//     mediump float tempU;
//     mediump float tempV;
//     tempY = curY - 32.0;
//     tempU = curU - 128.0;
//     tempV = curV - 128.0;
//     tempU = tempU * 1.1 + 0.5;
//     tempV = tempV * 1.1 + 0.5;
//     tempY = tempY * 1.1 + 0.5;
//     curY = tempY + 32.0;
//     curU = tempU + 128.0;
//     curV = tempV + 128.0;
      // orig end

     curY = microAdjust(curY, 16.0);
     curU = microAdjust(curU, 128.0);
     curV = microAdjust(curV, 128.0);


     curR = 1.164 * (curY  - 16.0) + 1.5958 * (curV - 128.0);
     curG = 1.164 * (curY - 16.0) - 0.81290 * (curV - 128.0) - 0.39173 * (curU - 128.0);
     curB = 1.164 * (curY - 16.0) + 2.017 * (curU - 128.0);


     curR /= 255.0;
     curG /= 255.0;
     curB /= 255.0;
     curR = min(max(curR, 0.0), 1.0);
     curG = min(max(curG, 0.0), 1.0);
     curB = min(max(curB, 0.0), 1.0);


     gl_FragColor = vec4(curR, curG, curB, 1.0);
}
