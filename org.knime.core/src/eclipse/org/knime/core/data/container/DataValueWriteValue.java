
package org.knime.core.data.container;

import org.knime.core.data.DataValue;
import org.knime.core.data2.values.NullableWriteValue;

/**
 * TODO
 *
 * @author Christian Dietz
 */
public interface DataValueWriteValue<D extends DataValue> extends NullableWriteValue {

	void setDataValue(D value);
}
