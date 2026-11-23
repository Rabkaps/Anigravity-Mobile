package com.hambalapps.agymobile.data.engine;

import android.os.Bundle;

interface IShizukuShellService {
    void destroy() = 16777114;
    Bundle exec(String command) = 1;
}
